package com.smartwithdraw.currency;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.security.NoteKeys;
import com.smartwithdraw.security.NoteValidator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class NoteFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private NoteFactory() {
    }

    public static ItemStack createNote(Currency currency, int value) {

        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();

        if (meta == null) {
            return note;
        }

        meta.setDisplayName(buildTitle(currency));
        meta.setCustomModelData(currency.customModelDataBase() + value);
        meta.setLore(buildLore(currency, value));

        if (SmartWithdraw.getInstance().getConfig().getBoolean("notes.glow-effect", false)) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        NamespacedKey markerKey = new NamespacedKey(plugin, NoteKeys.NOTE_MARKER);
        NamespacedKey valueKey = new NamespacedKey(plugin, NoteKeys.NOTE_VALUE);
        NamespacedKey currencyKey = new NamespacedKey(plugin, NoteKeys.NOTE_CURRENCY);
        NamespacedKey signatureKey = new NamespacedKey(plugin, NoteKeys.NOTE_SIGNATURE);

        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.INTEGER, value);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(currencyKey, PersistentDataType.STRING, currency.id());

        meta.getPersistentDataContainer().set(
                signatureKey,
                PersistentDataType.STRING,
                NoteValidator.sign(currency.id(), value)
        );

        note.setItemMeta(meta);

        return note;
    }

    private static String buildTitle(Currency currency) {

        String color = translate(currency.lore().titleColor());
        String typeLabel = SmartWithdraw.getInstance().getConfig()
                .getString("notes.type-label", "Satchel");

        String title = color + currency.name() + " " + typeLabel;

        if (currency.lore().showTier()) {
            title += " §7| §a§n" + currency.lore().tier();
        }

        return title;
    }

    private static List<String> buildLore(Currency currency, int value) {

        List<String> lore = new ArrayList<>();

        String serverName = SmartWithdraw.getInstance().getConfig()
                .getString("branding.server-name", "SmartWithdraw");

        String date = LocalDate.now().format(DATE_FORMAT);
        String typeLabel = SmartWithdraw.getInstance().getConfig()
                .getString("notes.type-label", "Satchel").toLowerCase();

        lore.add("§7Use this " + typeLabel + " to gain " + currency.lore().gainText() + "!");
        lore.add("");
        lore.add("§f§lInformation:");
        lore.add("§7- Value: §a" + currency.format(value));
        lore.add("§7- Created by: §6" + serverName + ", on " + date);
        lore.add("");
        lore.add("§7- Tax: §c" + currency.lore().taxPercent() + "%");
        lore.add("");
        lore.add("§7[Right-Click to use]");
        lore.add("§8[Shift+Right-Click stack to deposit all]");
        lore.add("§8[Sneak+Left-Click to split]");

        return lore;
    }

    private static String translate(String colorCode) {
        return ChatColor.translateAlternateColorCodes('&', colorCode);
    }
}
