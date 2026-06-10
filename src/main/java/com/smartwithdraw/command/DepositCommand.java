package com.smartwithdraw.command;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.security.NoteValidator;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class DepositCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            return true;
        }

        int deposited = 0;

        NamespacedKey valueKey =
                new NamespacedKey(
                        SmartWithdraw.getInstance(),
                        "note_value"
                );

        for (ItemStack item : player.getInventory().getContents()) {

            if (!NoteValidator.isValid(item)) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();

            if (meta == null) {
                continue;
            }

            Integer value = meta.getPersistentDataContainer().get(
                    valueKey,
                    PersistentDataType.INTEGER
            );

            if (value == null || value <= 0) {
                continue;
            }

            deposited += value * item.getAmount();
            player.getInventory().remove(item);
        }

        if (deposited <= 0) {
            player.sendMessage(
                    "§cYou have no valid notes to deposit."
            );
            return true;
        }

        EconomyManager.deposit(player, deposited);

        player.sendMessage(
                "§6§lSmartWithdraw §8» §aDeposited ₹" + deposited
        );

        return true;
    }
}
