package com.smartwithdraw.currency;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class NoteFactory {

    private NoteFactory() {
    }

    public static ItemStack createNote(int value) {

        return new ItemStack(Material.PAPER);
    }
}
