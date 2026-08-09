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

/**
 * A small read-only GUI showing all verified details of a held note.
 * Opened by /sw inspect while holding a SmartWithdraw note.
 */
public final class InspectMenu {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

    private InspectMenu() {
    }

    public static void open(Player player, ItemStack noteItem) {

        NoteInfo info = NoteValidator.getInfo(noteItem).orElse(null);

        Inventory inv = Bukkit.createInventory(null, 27,
                ChatColor.translateAlternateColorCodes('&',
                        "&6&lNote Inspector"));

        // Slot 13 — the note itself
        inv.setItem(13, noteItem.clone());

        // Slot 11 — Currency info
        inv.setItem(11, buildInfoItem(
                Material.BOOK,
                "&6Currency Details",
                List.of(
                        "&7ID: &f" + (info != null
                                ? info.currency().id() : "unknown"),
                        "&7Name: &f" + (info != null
                                ? info.currency().name() : "unknown"),
                        "&7Backend: &f" + (info != null
                                ? info.currency().backend().name() : "unknown"),
                        "&7Symbol: &f" + (info != null
                                ? info.currency().symbol() : "unknown")
                )
        ));

        // Slot 12 — Value info
        inv.setItem(12, buildInfoItem(
                Material.GOLD_INGOT,
                "&aValue",
                List.of(
                        "&7Raw value: &f" + (info != null
                                ? info.value() : "unknown"),
                        "&7Formatted: &a" + (info != null
                                ? info.currency().format(info.value()) : "unknown")
                )
        ));

        // Slot 14 — Expiry info
        String expiryLine;
        String timeLeft;

        if (info == null) {
            expiryLine = "&cunknown";
            timeLeft   = "&cunknown";
        } else if (info.currency().expiryDays() <= 0) {
            expiryLine = "&aNever";
            timeLeft   = "&aUnlimited";
        } else {
            long days = NoteValidator.daysRemaining(info);
            long expiryEpoch = info.createdAt()
                    + (long) info.currency().expiryDays() * 86_400_000L;
            expiryLine = DATE_FORMAT.format(Instant.ofEpochMilli(expiryEpoch));
            timeLeft = days <= 0 ? "&cExpired" : "&e" + days + " day(s)";
        }

        inv.setItem(14, buildInfoItem(
                Material.CLOCK,
                "&eExpiry Info",
                List.of(
                        "&7Created: &f" + (info != null && info.createdAt() > 0
                                ? DATE_FORMAT.format(
                                        Instant.ofEpochMilli(info.createdAt()))
                                : "unknown"),
                        "&7Expires: &f" + expiryLine,
                        "&7Time left: " + timeLeft
                )
        ));

        // Slot 15 — Signature / validity
        boolean valid   = info != null;
        boolean expired = valid && NoteValidator.isExpired(info);

        inv.setItem(15, buildInfoItem(
                valid && !expired ? Material.LIME_DYE : Material.RED_DYE,
                valid && !expired ? "&aSignature Valid" : "&cSignature Invalid / Expired",
                List.of(
                        "&7Verified: " + (valid ? "&a✔ Yes" : "&c✖ No"),
                        "&7Expired: " + (expired ? "&c✔ Yes" : "&a✖ No"),
                        "&7Currency exists: " + (valid ? "&a✔ Yes" : "&c✖ No")
                )
        ));

        // Slot 4 — Tax info
        inv.setItem(4, buildInfoItem(
                Material.COMPARATOR,
                "&cTax Info",
                List.of(
                        "&7Rate: &f" + (info != null
                                ? info.currency().tax().percent() + "%" : "unknown"),
                        "&7On withdraw: " + (info != null && info.currency()
                                .tax().applyOnWithdraw() ? "&c✔ Yes" : "&a✖ No"),
                        "&7On deposit: " + (info != null && info.currency()
                                .tax().applyOnDeposit()  ? "&c✔ Yes" : "&a✖ No")
                )
        ));

        player.openInventory(inv);
    }

    private static ItemStack buildInfoItem(Material material,
                                            String name,
                                            List<String> lore) {

        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(
                ChatColor.translateAlternateColorCodes('&', name));

        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(
                    ChatColor.translateAlternateColorCodes('&', line));
        }

        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }
}
