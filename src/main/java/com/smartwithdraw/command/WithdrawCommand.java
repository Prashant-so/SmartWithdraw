package com.smartwithdraw.command;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.currency.TaxConfig;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.util.CooldownManager;
import com.smartwithdraw.util.DailyLimitManager;
import com.smartwithdraw.util.InventoryUtils;
import com.smartwithdraw.util.Lang;
import com.smartwithdraw.util.SoundUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WithdrawCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                              String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            player.sendMessage("§cUsage: /withdraw <amount> [currency]");
            return true;
        }

        SmartWithdraw plugin = SmartWithdraw.getInstance();
        Currency currency    = CurrencyManager.getDefault();

        if (args.length == 2) {
            currency = CurrencyManager.get(args[1]).orElse(null);
            if (currency == null) {
                Lang.send(player, "unknown-currency", Map.of("currency", args[1]));
                return true;
            }
        }

        if (!currency.enabled()) {
            Lang.send(player, "currency-disabled");
            return true;
        }

        if (!currency.isWorldAllowed(player.getWorld().getName())) {
            Lang.send(player, "world-not-allowed",
                    Map.of("currency", currency.name()));
            return true;
        }

        if (!player.hasPermission("smartwithdraw.currency." + currency.id())) {
            Lang.send(player, "no-permission");
            return true;
        }

        BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());

        if (provider == null || !provider.isAvailable()) {
            Lang.send(player, "backend-unavailable",
                    Map.of("currency", currency.id()));
            return true;
        }

        int cooldownSeconds = plugin.getConfig()
                .getInt("limits.withdraw-cooldown-seconds", 0);
        long remaining = CooldownManager.remainingWithdrawSeconds(
                player, cooldownSeconds);

        if (remaining > 0) {
            Lang.send(player, "on-cooldown",
                    Map.of("seconds", String.valueOf(remaining)));
            return true;
        }

        int amount;
        try {
            long parsed = Long.parseLong(args[0]);
            if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
                Lang.send(player, "invalid-amount");
                return true;
            }
            amount = (int) parsed;
        } catch (NumberFormatException ex) {
            Lang.send(player, "invalid-amount");
            return true;
        }

        // Daily limit check — must happen BEFORE balance check so the
        // limit isn't bypassed by checking balance first
        if (currency.hasDailyLimit()) {
            boolean allowed = DailyLimitManager.tryWithdraw(
                    player.getUniqueId(), currency.id(),
                    amount, currency.dailyLimit());
            if (!allowed) {
                Lang.send(player, "daily-limit-reached", Map.of(
                        "limit",    currency.format(currency.dailyLimit()),
                        "currency", currency.name(),
                        "reset",    DailyLimitManager.resetString(
                                player.getUniqueId(), currency.id())
                ));
                return true;
            }
        }

        TaxConfig tax       = currency.tax();
        long taxAmount      = tax.applyOnWithdraw() ? tax.calculateTax(amount) : 0;
        long totalDeduct    = amount + taxAmount;

        if (!provider.has(player, totalDeduct)) {
            // Undo the daily limit reservation since we're not proceeding
            // (DailyLimitManager doesn't have a rollback, so re-deduct
            // from the recorded amount)
            if (currency.hasDailyLimit()) {
                DailyLimitManager.refund(
                        player.getUniqueId(), currency.id(), amount);
            }
            Lang.send(player, "insufficient-funds");
            return true;
        }

        long maxHeld = plugin.getConfig().getLong("limits.max-held-value", 0);
        if (maxHeld > 0) {
            long currentlyHeld = InventoryUtils.sumHeldNoteValue(player, currency);
            if (currentlyHeld + amount > maxHeld) {
                if (currency.hasDailyLimit()) {
                    DailyLimitManager.refund(
                            player.getUniqueId(), currency.id(), amount);
                }
                Lang.send(player, "held-limit-exceeded",
                        Map.of("limit", currency.format(maxHeld)));
                return true;
            }
        }

        provider.withdraw(player, totalDeduct);

        boolean autoSplit = plugin.getConfig()
                .getBoolean("notes.auto-split-notes", false);

        if (autoSplit) {
            Map<Integer, Integer> notes =
                    DenominationCalculator.calculate(amount, currency);
            for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    InventoryUtils.give(player,
                            NoteFactory.createNote(currency, entry.getKey()));
                }
            }
        } else {
            InventoryUtils.give(player, NoteFactory.createNote(currency, amount));
        }

        CooldownManager.markWithdraw(player);
        TransactionLogger.log("WITHDRAW", player, currency.id(), amount);
        SoundUtil.play(player, "withdraw");

        if (taxAmount > 0) {
            TransactionLogger.log("TAX_WITHDRAW", player,
                    currency.id(), taxAmount);
            Lang.send(player, "withdraw-success-taxed", Map.of(
                    "amount", currency.format(amount),
                    "tax",    currency.format(taxAmount)
            ));
        } else {
            Lang.send(player, "withdraw-success",
                    Map.of("amount", currency.format(amount)));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            CurrencyManager.getAllEnabled().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .forEach(completions::add);
        }
        return completions;
    }
}
