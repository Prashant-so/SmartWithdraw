package com.smartwithdraw.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class InventoryUtils {

private InventoryUtils() {
}

public static void give(Player player, ItemStack item) {

    Map<Integer, ItemStack> leftover =
            player.getInventory().addItem(item);

    if (!leftover.isEmpty()) {

        leftover.values().forEach(stack ->
                player.getWorld().dropItemNaturally(
                        player.getLocation(),
                        stack
                )
        );
    }
}

}
