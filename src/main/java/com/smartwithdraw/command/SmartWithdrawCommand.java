package com.smartwithdraw.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SmartWithdrawCommand implements CommandExecutor {

@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    sender.sendMessage("§6§l💰 SmartWithdraw Help");
    sender.sendMessage("");
    sender.sendMessage("§e/withdraw <amount> §7➜ Convert money into notes");
    sender.sendMessage("§aRight Click Note §7➜ Deposit note");
    sender.sendMessage("§b/sw help §7➜ Open help menu");
    sender.sendMessage("§d/smartwithdraw help §7➜ Open help menu");
    sender.sendMessage("");
    sender.sendMessage("§6Supported Notes:");
    sender.sendMessage("§a₹1  §6₹10  §b₹50");
    sender.sendMessage("§d₹100  §e₹500  §5₹2000");
    sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

    return true;
}

}
