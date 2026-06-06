package com.smartwithdraw.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class WithdrawCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

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

            sender.sendMessage("§aRequested withdrawal: §e₹" + amount);

        } catch (NumberFormatException e) {
            sender.sendMessage("§cPlease enter a valid number.");
        }

        return true;
    }
}
