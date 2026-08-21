package com.smartwithdraw.command;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.TaxConfig;
import com.smartwithdraw.listener.NoteExpiryListener;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.util.CooldownManager;
import com.smartwithdraw.util.Lang;
import com.smartwithdraw.util.SoundUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DepositCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                              String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        NoteExpiryListener.scanAndDestroy(player);

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        int cooldownSeconds = plugin.getConfig()
                .getInt("limits.deposit-cooldown-seconds", 0);
        long remaining = CooldownManager.remainingDepositSeconds(player, cooldownSeconds);

        if (remaining > 0) {
            Lang.send(player, "on-cooldown", Map.of("seconds", String.valueOf(remaining)));
            return true;
        }

        Map<String, Long>     depositedByCurrency = new HashMap<>();
        Map<String, Currency> currencyById        = new HashMap<>();
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
            return true;
        }

        List<String> successfullyCredited = new ArrayList<>();

        for (Map.Entry<String, Long> entry : depositedByCurrency.entrySet()) {

            Currency currency = currencyById.get(entry.getKey());
            long rawAmount    = entry.getValue();

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
            CooldownManager.markDeposit(player);
            TransactionLogger.log("DEPOSIT", player, currency.id(), creditAmount);
            SoundUtil.play(player, "deposit");

            if (taxAmount > 0) {
                TransactionLogger.log("TAX_DEPOSIT", player, currency.id(), taxAmount);
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

        return true;
    }
}
