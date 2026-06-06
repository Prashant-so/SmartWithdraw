package com.smartwithdraw.command;

import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

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

        int amount;

        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a valid amount.");
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

        Map<Integer, Integer> notes = DenominationCalculator.calculate(amount);

        for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {

            int value = entry.getKey();
            int count = entry.getValue();

            for (int i = 0; i < count; i++) {
                ItemStack note = NoteFactory.createNote(value);
                player.getInventory().addItem(note);
            }
        }

        player.sendMessage(ChatColor.GREEN + "You withdrew ₹" + amount + " successfully.");

        return true;
    }
}
