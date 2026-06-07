package com.smartwithdraw.command;

import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class WithdrawCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /withdraw <amount>");
            return true;
        }

        try {

            int amount = Integer.parseInt(args[0]);

            if (amount <= 0) {
                player.sendMessage("§cAmount must be greater than 0.");
                return true;
            }

            if (!EconomyManager.has(player, amount)) {
                player.sendMessage("§cNot enough money.");
                return true;
            }

            Map<Integer, Integer> notes =
                    DenominationCalculator.calculate(amount);

            player.sendMessage("§6§lSmartWithdraw §8» §aBreakdown:");

            for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {

                player.sendMessage(
                        "§e₹" + entry.getKey()
                                + " §7x §f"
                                + entry.getValue()
                );
            }

        } catch (NumberFormatException ex) {

            player.sendMessage("§cPlease enter a valid number.");

        }

        return true;
    }
}
