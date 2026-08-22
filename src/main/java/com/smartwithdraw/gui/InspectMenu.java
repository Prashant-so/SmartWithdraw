package com.smartwithdraw.gui;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class InspectMenu {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

    private InspectMenu() {
    }

    public static void open(Player player, ItemStack noteItem) {

        NoteInfo info = NoteValidator.getInfo(noteItem).orElse(null);

        Inventory inv = Bukkit.createInventory(null, 54,
                ChatColor.translateAlternateColorCodes('&', "&8&lNote Inspector"));

        fillGlass(inv);

        // Slot 13 — the note itself
        inv.setItem(13, noteItem.clone());

        // Slot 11 — currency name only (no backend, no symbol)
        inv.setItem(11, buildItem(Material.BOOK, "&6Currency", List.of(
                "&7Name: &f" + (info != null ? info.currency().name() : "Unknown"),
                "&7ID: &f"   + (info != null ? info.currency().id()   : "Unknown")
        )));

        // Slot 12 — value
        inv.setItem(12, buildItem(Material.GOLD_INGOT, "&aValue", List.of(
                "&7Amount: &a" + (info != null
                        ? info.currency().format(info.value()) : "Unknown"),
                "&7Raw: &f"   + (info != null ? info.value() : "Unknown")
        )));

        // Slot 14 — expiry
        String expiryLine, timeLeft;
        if (info == null) {
            expiryLine = "&cUnknown";
            timeLeft   = "&cUnknown";
        } else if (info.currency().expiryDays() <= 0) {
            expiryLine = "&aNever";
            timeLeft   = "&aUnlimited";
        } else {
            long days        = NoteValidator.daysRemaining(info);
            long expiryEpoch = info.createdAt()
                    + (long) info.currency().expiryDays() * 86_400_000L;
            expiryLine = DATE_FORMAT.format(Instant.ofEpochMilli(expiryEpoch));
            timeLeft   = days <= 0 ? "&cExpired" : "&e" + days + " day(s)";
        }

        inv.setItem(14, buildItem(Material.CLOCK, "&eExpiry", List.of(
                "&7Created: &f" + (info != null && info.createdAt() > 0
                        ? DATE_FORMAT.format(Instant.ofEpochMilli(info.createdAt()))
                        : "Unknown"),
                "&7Expires: &f" + expiryLine,
                "&7Remaining: " + timeLeft
        )));

        // Slot 15 — validity
        boolean valid   = info != null;
        boolean expired = valid && NoteValidator.isExpired(info);

        inv.setItem(15, buildItem(
                valid && !expired ? Material.LIME_DYE : Material.RED_DYE,
                valid && !expired ? "&aSignature Valid" : "&cInvalid or Expired",
                List.of(
                        "&7Verified: "  + (valid   ? "&a✔ Yes" : "&c✖ No"),
                        "&7Expired: "   + (expired ? "&c✔ Yes" : "&a✖ No")
                )
        ));

        // Slot 31 — tax info
        inv.setItem(31, buildItem(Material.COMPARATOR, "&cTax", List.of(
                "&7Rate: &f" + (info != null
                        ? info.currency().tax().percent() + "%" : "Unknown"),
                "&7On withdraw: " + (info != null && info.currency().tax().applyOnWithdraw()
                        ? "&c✔ Yes" : "&a✖ No"),
                "&7On deposit: " + (info != null && info.currency().tax().applyOnDeposit()
                        ? "&c✔ Yes" : "&a✖ No")
        )));

        // Slot 49 — close
        inv.setItem(49, BankMenu.buildItem(
                Material.RED_STAINED_GLASS_PANE,
                "§c§lClose",
                List.of("§7Click to close."),
                null
        ));

        player.openInventory(inv);
    }

    private static void fillGlass(Inventory inv) {

        ItemStack pane = buildItem(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of());

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }

    private static ItemStack buildItem(Material material, String name,
                                        List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> colored = new ArrayList<>();
        for (String line : lore) {
            colored.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(colored);
        item.setItemMeta(meta);
        return item;
    }
}
