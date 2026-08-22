package com.smartwithdraw.listener;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.currency.TaxConfig;
import com.smartwithdraw.gui.BankMenu;
import com.smartwithdraw.gui.BankMenuHolder;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.util.CooldownManager;
import com.smartwithdraw.util.DailyLimitManager;
import com.smartwithdraw.util.InventoryUtils;
import com.smartwithdraw.util.Lang;
import com.smartwithdraw.util.SoundUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BankMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {

        if (!(event.getInventory().getHolder() instanceof BankMenuHolder holder)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(
                new NamespacedKey(SmartWithdraw.getInstance(), BankMenu.ACTION_KEY),
                PersistentDataType.STRING);

        if (action == null) return;

        Player player = (Player) event.getWhoClicked();

        switch (action) {

            case BankMenu.CLOSE_ACTION -> player.closeInventory();

            case BankMenu.BACK_ACTION -> BankMenu.openSelection(player);

            case BankMenu.DEPOSIT_ALL_ACTION -> depositAll(player);

            case BankMenu.DEPOSIT_HAND_ACTION -> depositHand(player,
                    holder.getCurrency() != null
                            ? holder.getCurrency()
                            : CurrencyManager.getDefault());

            default -> {

                if (action.startsWith(BankMenu.SWITCH_CURRENCY_ACTION_PREFIX)) {
                    String id = action.substring(
                            BankMenu.SWITCH_CURRENCY_ACTION_PREFIX.length());
                    CurrencyManager.get(id).ifPresentOrElse(
                            c -> BankMenu.openDetail(player, c),
                            () -> BankMenu.openSelection(player));
                    return;
                }

                if (action.startsWith(BankMenu.WITHDRAW_ACTION_PREFIX)) {
                    Currency currency = holder.getCurrency() != null
                            ? holder.getCurrency()
                            : CurrencyManager.getDefault();
                    int value = Integer.parseInt(
                            action.substring(BankMenu.WITHDRAW_ACTION_PREFIX.length()));
                    withdrawOne(player, currency, value);
                }
            }
        }
    }

    private void withdrawOne(Player player, Currency currency, int value) {

        if (!currency.isWorldAllowed(player.getWorld().getName())) {
            Lang.send(player, "world-not-allowed", Map.of("currency", currency.name()));
            return;
        }

        BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());

        if (provider == null || !provider.isAvailable()) {
            Lang.send(player, "backend-unavailable", Map.of("currency", currency.id()));
            return;
        }

        int cooldownSeconds = SmartWithdraw.getInstance().getConfig()
                .getInt("limits.withdraw-cooldown-seconds", 0);
        long remaining = CooldownManager.remainingWithdrawSeconds(player, cooldownSeconds);

        if (remaining > 0) {
            Lang.send(player, "on-cooldown", Map.of("seconds", String.valueOf(remaining)));
            return;
        }

        if (currency.hasDailyLimit()) {
            boolean allowed = DailyLimitManager.tryWithdraw(
                    player.getUniqueId(), currency.id(), value, currency.dailyLimit());
            if (!allowed) {
                Lang.send(player, "daily-limit-reached", Map.of(
                        "limit",    currency.format(currency.dailyLimit()),
                        "currency", currency.name(),
                        "reset",    DailyLimitManager.resetString(
                                player.getUniqueId(), currency.id())
                ));
                return;
            }
        }

        TaxConfig tax    = currency.tax();
        long taxAmount   = tax.applyOnWithdraw() ? tax.calculateTax(value) : 0;
        long totalDeduct = value + taxAmount;

        if (!provider.has(player, totalDeduct)) {
            if (currency.hasDailyLimit()) {
                DailyLimitManager.refund(player.getUniqueId(), currency.id(), value);
            }
            Lang.send(player, "insufficient-funds");
            return;
        }

        long maxHeld = SmartWithdraw.getInstance().getConfig()
                .getLong("limits.max-held-value", 0);
        if (maxHeld > 0) {
            long held = com.smartwithdraw.util.InventoryUtils.sumHeldNoteValue(player, currency);
            if (held + value > maxHeld) {
                if (currency.hasDailyLimit()) {
                    DailyLimitManager.refund(player.getUniqueId(), currency.id(), value);
                }
                Lang.send(player, "held-limit-exceeded",
                        Map.of("limit", currency.format(maxHeld)));
                return;
            }
        }

        ItemStack note = NoteFactory.createNote(currency, value);
        if (note == null) {
            if (currency.hasDailyLimit()) {
                DailyLimitManager.refund(player.getUniqueId(), currency.id(), value);
            }
            player.sendMessage("§c§l✖ §cNote creation failed — transaction aborted.");
            return;
        }

        boolean withdrawn = provider.withdraw(player, totalDeduct);
        if (!withdrawn) {
            if (currency.hasDailyLimit()) {
                DailyLimitManager.refund(player.getUniqueId(), currency.id(), value);
            }
            Lang.send(player, "insufficient-funds");
            return;
        }

        InventoryUtils.give(player, note);
        CooldownManager.markWithdraw(player);
        TransactionLogger.log("WITHDRAW", player, currency.id(), value);
        SoundUtil.play(player, "withdraw");

        if (taxAmount > 0) {
            TransactionLogger.log("TAX_WITHDRAW", player, currency.id(), taxAmount);
            Lang.send(player, "withdraw-success-taxed", Map.of(
                    "amount", currency.format(value),
                    "tax",    currency.format(taxAmount)
            ));
        } else {
            Lang.send(player, "withdraw-success",
                    Map.of("amount", currency.format(value)));
        }
    }

    private void depositHand(Player player, Currency currency) {

        ItemStack held = player.getInventory().getItemInMainHand();

        if (held == null || held.getType().isAir()) {
            player.sendMessage("§c§l✖ §cYou are not holding a note.");
            return;
        }

        Optional<NoteInfo> infoOpt = NoteValidator.getInfo(held);

        if (infoOpt.isEmpty()) {
            player.sendMessage("§c§l✖ §cThat item is not a valid note.");
            return;
        }

        NoteInfo info = infoOpt.get();

        if (NoteValidator.isExpired(info)) {
            NoteExpiryListener.scanAndDestroy(player);
            return;
        }

        BalanceProvider provider =
                BalanceProviderRegistry.get(info.currency().backend());

        if (provider == null || !provider.isAvailable()) {
            Lang.send(player, "backend-unavailable",
                    Map.of("currency", info.currency().id()));
            return;
        }

        long total = (long) info.value() * held.getAmount();

        TaxConfig tax     = info.currency().tax();
        long taxAmount    = tax.applyOnDeposit() ? tax.calculateTax(total) : 0;
        long creditAmount = Math.max(0, total - taxAmount);

        boolean deposited = provider.deposit(player, creditAmount);

        if (!deposited) {
            Lang.send(player, "backend-unavailable",
                    Map.of("currency", info.currency().id()));
            return;
        }

        player.getInventory().setItemInMainHand(null);
        TransactionLogger.log("DEPOSIT", player, info.currency().id(), creditAmount);
        SoundUtil.play(player, "deposit");

        if (taxAmount > 0) {
            TransactionLogger.log("TAX_DEPOSIT", player,
                    info.currency().id(), taxAmount);
            Lang.send(player, "deposit-success-taxed", Map.of(
                    "amount", info.currency().format(creditAmount),
                    "tax",    info.currency().format(taxAmount)
            ));
        } else {
            Lang.send(player, "deposit-success",
                    Map.of("amount", info.currency().format(creditAmount)));
        }
    }

    private void depositAll(Player player) {

        NoteExpiryListener.scanAndDestroy(player);

        Map<String, Long>     depositedByCurrency = new HashMap<>();
        Map<String, com.smartwithdraw.currency.Currency> currencyById = new HashMap<>();
        List<ItemStack>       toRemove            = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {

            Optional<NoteInfo> info = NoteValidator.getInfo(item);
            if (info.isEmpty()) continue;

            NoteInfo note = info.get();

            if (!note.currency().isWorldAllowed(player.getWorld().getName())) continue;

            depositedByCurrency.merge(
                    note.currency().id(),
                    (long) note.value() * item.getAmount(),
                    Long::sum);

            currencyById.put(note.currency().id(), note.currency());
            toRemove.add(item);
        }

        if (depositedByCurrency.isEmpty()) {
            Lang.send(player, "no-notes-to-deposit");
            return;
        }

        List<String> successfullyCredited = new ArrayList<>();

        for (Map.Entry<String, Long> entry : depositedByCurrency.entrySet()) {

            com.smartwithdraw.currency.Currency currency =
                    currencyById.get(entry.getKey());
            long rawAmount = entry.getValue();

            BalanceProvider provider =
                    BalanceProviderRegistry.get(currency.backend());

            if (provider == null || !provider.isAvailable()) {
                Lang.send(player, "backend-unavailable",
                        Map.of("currency", currency.id()));
                continue;
            }

            TaxConfig tax     = currency.tax();
            long taxAmount    = tax.applyOnDeposit()
                    ? tax.calculateTax(rawAmount) : 0;
            long creditAmount = Math.max(0, rawAmount - taxAmount);

            boolean deposited = provider.deposit(player, creditAmount);

            if (!deposited) {
                Lang.send(player, "backend-unavailable",
                        Map.of("currency", currency.id()));
                continue;
            }

            successfullyCredited.add(currency.id());
            TransactionLogger.log("DEPOSIT", player, currency.id(), creditAmount);
            SoundUtil.play(player, "deposit");

            if (taxAmount > 0) {
                TransactionLogger.log("TAX_DEPOSIT", player,
                        currency.id(), taxAmount);
                Lang.send(player, "deposit-success-taxed", Map.of(
                        "amount", currency.format(creditAmount),
                        "tax",    currency.format(taxAmount)
                ));
            } else {
                Lang.send(player, "deposit-success",
                        Map.of("amount", currency.format(creditAmount)));
            }
        }

        for (ItemStack item : toRemove) {
            Optional<NoteInfo> info = NoteValidator.getInfo(item);
            if (info.isEmpty()) continue;
            if (successfullyCredited.contains(info.get().currency().id())) {
                player.getInventory().remove(item);
            }
        }
    }
}
