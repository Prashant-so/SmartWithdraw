package com.smartwithdraw.command;

import com.smartwithdraw.currency.DenominationCalculator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class WithdrawCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /withdraw <amount>");
            return true;
        }

        try {

            int amount = Integer.parseInt(args[0]);

            if (amount <= 0) {
                sender.sendMessage("§cAmount must be greater than 0.");
                return true;
            }

            Map<Integer, Integer> notes =
                    DenominationCalculator.calculate(amount);

            sender.sendMessage("§6§lSmartWithdraw §8» §aWithdrawal Breakdown");

            for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {

                sender.sendMessage(
                        "§e₹" + entry.getKey()
                                + " §7x §f"
                                + entry.getValue()
                );
            }

        } catch (NumberFormatException ex) {

            sender.sendMessage("§cPlease enter a valid number.");

        }

        return true;
    }
}
