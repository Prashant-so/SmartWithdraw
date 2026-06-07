package com.smartwithdraw.security;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class NoteValidator {


private NoteValidator() {
}

public static boolean isValid(ItemStack item) {

    if (item == null) {
        return false;
    }

    if (!item.hasItemMeta()) {
        return false;
    }

    ItemMeta meta = item.getItemMeta();

    if (meta == null) {
        return false;
    }

    return meta.hasDisplayName();
}


}
