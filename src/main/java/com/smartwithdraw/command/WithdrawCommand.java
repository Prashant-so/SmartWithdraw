package com.smartwithdraw.command;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.util.CooldownManager;
import com.smartwithdraw.util.InventoryUtils;
import com.smartwithdraw.util.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Usage:
 *   /withdraw <amount>              -> withdraws from the default currency
 *   /withdraw <amount> <currency>   -> withdraws from a specific currency
 */
public class WithdrawCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender,
                              Command command,
                              String label,
                              String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            player.sendMessage("§cUsage: /withdraw <amount> [currency]");
            return true;
        }

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        Currency currency = CurrencyManager.getDefault();

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

        if (!player.hasPermission("smartwithdraw.currency." + currency.id())) {
            Lang.send(player, "no-permission");
            return true;
        }

        BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());

        if (provider == null || !provider.isAvailable()) {
            Lang.send(player, "backend-unavailable", Map.of("currency", currency.id()));
            return true;
        }

        int cooldownSeconds = plugin.getConfig()
                .getInt("limits.withdraw-cooldown-seconds", 0);

        long remaining = CooldownManager.remainingWithdrawSeconds(player, cooldownSeconds);

        if (remaining > 0) {
            Lang.send(player, "on-cooldown", Map.of("seconds", String.valueOf(remaining)));
            return true;
        }

        try {

            int amount = Integer.parseInt(args[0]);

            if (amount <= 0) {
                Lang.send(player, "invalid-amount");
                return true;
            }

            if (!provider.has(player, amount)) {
                Lang.send(player, "insufficient-funds");
                return true;
            }

            long maxHeld = plugin.getConfig().getLong("limits.max-held-value", 0);

            if (maxHeld > 0) {

                long currentlyHeld = InventoryUtils.sumHeldNoteValue(player, currency);

                if (currentlyHeld + amount > maxHeld) {
                    Lang.send(player, "held-limit-exceeded",
                            Map.of("limit", currency.format(maxHeld)));
                    return true;
                }
            }

            provider.withdraw(player, amount);

            boolean autoSplit = plugin.getConfig()
                    .getBoolean("notes.auto-split-notes", false);

            if (autoSplit) {

                Map<Integer, Integer> notes = DenominationCalculator.calculate(amount, currency);

                for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {

                    int value = entry.getKey();
                    int count = entry.getValue();

                    for (int i = 0; i < count; i++) {
                        InventoryUtils.give(player, NoteFactory.createNote(currency, value));
                    }
                }

            } else {
                InventoryUtils.give(player, NoteFactory.createNote(currency, amount));
            }

            CooldownManager.markWithdraw(player);
            TransactionLogger.log("WITHDRAW", player, currency.id(), amount);

            Lang.send(player, "withdraw-success",
                    Map.of("amount", currency.format(amount)));

        } catch (NumberFormatException ex) {
            Lang.send(player, "invalid-amount");
        }

        return true;
    }
}
