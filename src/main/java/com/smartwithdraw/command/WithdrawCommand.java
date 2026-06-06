package com.smartwithdraw.command;

import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WithdrawCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /withdraw <amount>");
            return true;
        }

        double amount;

        try {
            amount = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a valid number.");
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
            return true;
        }

        if (EconomyManager.getBalance(player) < amount) {
            player.sendMessage(ChatColor.RED + "You don't have enough money.");
            return true;
        }

        if (!EconomyManager.withdraw(player, amount)) {
            player.sendMessage(ChatColor.RED + "Transaction failed.");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Successfully withdrew ₹" + amount + ".");

        return true;
    }
}
