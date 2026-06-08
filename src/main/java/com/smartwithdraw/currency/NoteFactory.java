package com.smartwithdraw.currency;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
    meta.setCustomModelData(getModelData(value));

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

    NamespacedKey valueKey =
            new NamespacedKey(SmartWithdraw.getInstance(), "note_value");

    NamespacedKey noteKey =
            new NamespacedKey(SmartWithdraw.getInstance(), "smartwithdraw_note");

    meta.getPersistentDataContainer().set(
            valueKey,
            PersistentDataType.INTEGER,
            value
    );

    meta.getPersistentDataContainer().set(
            noteKey,
            PersistentDataType.BYTE,
            (byte) 1
    );

    note.setItemMeta(meta);

    return note;
}

private static int getModelData(int value) {
    return switch (value) {
        case 1 -> 1001;
        case 10 -> 1010;
        case 50 -> 1050;
        case 100 -> 1100;
        case 500 -> 1500;
        case 2000 -> 2000;
        default -> 9999;
    };
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
