package com.smartwithdraw.currency;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class NoteFactory {

    public static ItemStack createNote(int value) {

        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();

        if (meta == null) {
            return note;
        }

        meta.setDisplayName(ChatColor.GOLD + "✦ ₹" + value + " Bank Note ✦");

        List<String> lore = new ArrayList<>();

        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Value: ₹" + value);
        lore.add("");
        lore.add(ChatColor.GREEN + "▶ Right-click to deposit");
        lore.add(ChatColor.GREEN + "into your bank account");
        lore.add("");
        lore.add(ChatColor.GRAY + "Issued by:");
        lore.add(ChatColor.GOLD + "SmartWithdraw Bank");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");

        meta.setLore(lore);

        note.setItemMeta(meta);

        return note;
    }
}
