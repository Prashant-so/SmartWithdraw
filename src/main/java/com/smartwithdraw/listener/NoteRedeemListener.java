package com.smartwithdraw.listener;

import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class NoteRedeemListener implements Listener {

    @EventHandler
    public void onNoteRedeem(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.PAPER) {
            return;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return;
        }

        List<String> lore = item.getItemMeta().getLore();

        if (lore == null) {
            return;
        }

        int value = -1;

        for (String line : lore) {

            if (line.contains("Value:")) {

                try {
                    value = Integer.parseInt(
                            line.replace("§fValue: §a₹", "")
                                    .replace(",", "")
                                    .trim()
                    );
                } catch (Exception ignored) {
                }

                break;
            }
        }

        if (value <= 0) {
            return;
        }

        Player player = event.getPlayer();

        EconomyManager.deposit(player, value);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage(
                "§6§lSmartWithdraw §8» §aDeposited ₹" + value
        );

        event.setCancelled(true);
    }
}
