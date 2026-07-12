package com.smartwithdraw.command;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.util.CooldownManager;
import com.smartwithdraw.util.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DepositCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender,
                              Command command,
                              String label,
                              String[] args) {

        if (!(sender instanceof Player player)) {
            return true;
        }

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        int cooldownSeconds = plugin.getConfig()
                .getInt("limits.deposit-cooldown-seconds", 0);

        long remaining = CooldownManager.remainingDepositSeconds(player, cooldownSeconds);

        if (remaining > 0) {
            Lang.send(player, "on-cooldown", Map.of("seconds", String.valueOf(remaining)));
            return true;
        }

        Map<String, Long> depositedByCurrency = new HashMap<>();
        Map<String, Currency> currencyById = new HashMap<>();

        for (ItemStack item : player.getInventory().getContents()) {

            Optional<NoteInfo> info = NoteValidator.getInfo(item);

            if (info.isEmpty()) {
                continue;
            }

            NoteInfo note = info.get();

            depositedByCurrency.merge(
                    note.currency().id(),
                    (long) note.value() * item.getAmount(),
                    Long::sum
            );

            currencyById.put(note.currency().id(), note.currency());

            player.getInventory().remove(item);
        }

        if (depositedByCurrency.isEmpty()) {
            Lang.send(player, "no-notes-to-deposit");
            return true;
        }

        for (Map.Entry<String, Long> entry : depositedByCurrency.entrySet()) {

            Currency currency = currencyById.get(entry.getKey());
            long amount = entry.getValue();

            BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());

            if (provider == null || !provider.isAvailable()) {
                Lang.send(player, "backend-unavailable", Map.of("currency", currency.id()));
                continue;
            }

            provider.deposit(player, amount);
            CooldownManager.markDeposit(player);
            TransactionLogger.log("DEPOSIT", player, currency.id(), amount);

            Lang.send(player, "deposit-success",
                    Map.of("amount", currency.format(amount)));
        }

        return true;
    }
}
