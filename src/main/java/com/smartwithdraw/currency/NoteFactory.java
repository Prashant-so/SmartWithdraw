package com.smartwithdraw.currency;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.security.NoteKeys;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import org.bukkit.ChatColor;
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

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static NamespacedKey markerKey;
    private static NamespacedKey valueKey;
    private static NamespacedKey currencyKey;
    private static NamespacedKey signatureKey;
    private static NamespacedKey createdKey;

    private NoteFactory() {
    }

    public static void init() {
        SmartWithdraw plugin = SmartWithdraw.getInstance();
        markerKey    = new NamespacedKey(plugin, NoteKeys.NOTE_MARKER);
        valueKey     = new NamespacedKey(plugin, NoteKeys.NOTE_VALUE);
        currencyKey  = new NamespacedKey(plugin, NoteKeys.NOTE_CURRENCY);
        signatureKey = new NamespacedKey(plugin, NoteKeys.NOTE_SIGNATURE);
        createdKey   = new NamespacedKey(plugin, NoteKeys.NOTE_CREATED);
    }

    public static ItemStack createNote(Currency currency, int value) {

        ItemStack note = new ItemStack(currency.material());
        ItemMeta  meta = note.getItemMeta();

        if (meta == null) return note;

        long now = System.currentTimeMillis();

        meta.setDisplayName(buildTitle(currency));
        meta.setCustomModelData(currency.customModelDataBase() + value);
        meta.setLore(buildLore(currency, value, now));

        if (SmartWithdraw.getInstance().getConfig()
                .getBoolean("notes.glow-effect", false)) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.getPersistentDataContainer()
                .set(valueKey,     PersistentDataType.INTEGER, value);
        meta.getPersistentDataContainer()
                .set(markerKey,    PersistentDataType.BYTE,    (byte) 1);
        meta.getPersistentDataContainer()
                .set(currencyKey,  PersistentDataType.STRING,  currency.id());
        meta.getPersistentDataContainer()
                .set(signatureKey, PersistentDataType.STRING,
                        NoteValidator.sign(currency.id(), value));
        meta.getPersistentDataContainer()
                .set(createdKey,   PersistentDataType.LONG,    now);

        note.setItemMeta(meta);

        return note;
    }

    private static String buildTitle(Currency currency) {

        String color     = translate(currency.lore().titleColor());
        String typeLabel = SmartWithdraw.getInstance().getConfig()
                .getString("notes.type-label", "Satchel");

        String title = color + currency.name() + " " + typeLabel;

        if (currency.lore().showTier()) {
            title += " §7| §a§n" + currency.lore().tier();
        }

        return title;
    }

    private static List<String> buildLore(Currency currency, int value, long createdAt) {

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        String serverName = plugin.getConfig()
                .getString("branding.server-name", "SmartWithdraw");
        String date       = LocalDate.now().format(DATE_FORMAT);
        String typeLabel  = plugin.getConfig()
                .getString("notes.type-label", "Satchel").toLowerCase();

        // Build expiry string
        String expiryStr;
        if (currency.expiryDays() <= 0) {
            expiryStr = "Never";
        } else {
            NoteInfo temp = new NoteInfo(currency, value, createdAt);
            long days = NoteValidator.daysRemaining(temp);
            expiryStr = days <= 0 ? "Expired" : days + " day" + (days == 1 ? "" : "s");
        }

        List<String> template = currency.lore().template();

        if (template == null || template.isEmpty()) {
            template = List.of(
                    "&7Use this " + typeLabel + " to gain "
                            + currency.lore().gainText() + "!",
                    "",
                    "&f&lInformation:",
                    "&7- Value: &a%value%",
                    "&7- Created by: &6%server%, on %date%",
                    "&7- Expires in: &e%expiry%",
                    "",
                    "&7- Tax: &c%tax%",
                    "",
                    "&7[Right-Click to use]",
                    "&8[Shift+Right-Click stack to deposit all]",
                    "&8[Sneak+Left-Click to split]"
            );
        }

        List<String> lore = new ArrayList<>();

        for (String line : template) {
            String processed = line
                    .replace("%value%",    currency.format(value))
                    .replace("%server%",   serverName)
                    .replace("%date%",     date)
                    .replace("%expiry%",   expiryStr)
                    .replace("%tax%",      currency.tax().displayString())
                    .replace("%currency%", currency.name())
                    .replace("%type%",     typeLabel);

            lore.add(translate(processed));
        }

        return lore;
    }

    private static String translate(String colorCode) {
        return ChatColor.translateAlternateColorCodes('&', colorCode);
    }
}
