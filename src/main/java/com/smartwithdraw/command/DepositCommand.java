package com.smartwithdraw.command;

import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class DepositCommand implements CommandExecutor {

@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

    if (!(sender instanceof Player player)) {
        return true;
    }

    int deposited = 0;

    for (ItemStack item : player.getInventory().getContents()) {

        if (item == null || item.getType() != Material.PAPER) {
            continue;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            continue;
        }

        List<String> lore = item.getItemMeta().getLore();

        if (lore == null) {
            continue;
        }

        for (String line : lore) {

            if (line.contains("Value:")) {

                try {

                    int value = Integer.parseInt(
                            line.replace("§fValue: §a₹", "")
                                    .replace(",", "")
                                    .trim()
                    );

                    deposited += value * item.getAmount();
                    player.getInventory().remove(item);

                } catch (Exception ignored) {
                }

                break;
            }
        }
    }

    if (deposited <= 0) {
        player.sendMessage("§cYou have no notes to deposit.");
        return true;
    }

    EconomyManager.deposit(player, deposited);

    player.sendMessage(
            "§6§lSmartWithdraw §8» §aDeposited ₹" + deposited
    );

    return true;
}

}
