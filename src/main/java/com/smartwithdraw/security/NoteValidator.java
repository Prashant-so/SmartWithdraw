package com.smartwithdraw.security;

import org.bukkit.inventory.ItemStack;

public final class NoteValidator {

    private NoteValidator() {
    }

    public static boolean isValid(ItemStack item) {

        if (item == null) {
            return false;
        }

        return item.hasItemMeta();
    }
}
