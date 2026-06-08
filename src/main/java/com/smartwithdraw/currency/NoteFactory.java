package com.smartwithdraw.currency;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class NoteFactory {

private NoteFactory() {
}

public static ItemStack createNote(int value) {

    ItemStack note = new ItemStack(Material.PAPER);
    ItemMeta meta = note.getItemMeta();

    if (meta == null) {
        return note;
    }

    meta.setDisplayName(getName(value));

    List<String> lore = new ArrayList<>();

    lore.add("§8━━━━━━━━━━━━━━━━━━");
    lore.add("");
    lore.add("§fValue: §a₹" + value);
    lore.add("");
    lore.add("§e▶ Right-Click to deposit");
    lore.add("§7into your account");
    lore.add("");
    lore.add("§bSmart Withdraw Bank");
    lore.add("");
    lore.add("§8━━━━━━━━━━━━━━━━━━");

    meta.setLore(lore);

    note.setItemMeta(meta);

    return note;
}

private static String getName(int value) {

    return switch (value) {
        case 1 -> "§a✦ ₹1 Note ✦";
        case 10 -> "§6✦ ₹10 Note ✦";
        case 50 -> "§b✦ ₹50 Note ✦";
        case 100 -> "§d✦ ₹100 Note ✦";
        case 500 -> "§e✦ ₹500 Note ✦";
        case 2000 -> "§5✦ ₹2000 Note ✦";
        default -> "§f✦ ₹" + value + " Note ✦";
    };
}

}
