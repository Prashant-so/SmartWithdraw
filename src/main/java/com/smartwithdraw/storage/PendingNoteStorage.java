package com.smartwithdraw.storage;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores notes for offline players in pending-notes.yml.
 * Each entry: UUID -> list of "currencyId:amount" strings.
 * Delivered and removed on next login.
 */
public final class PendingNoteStorage {

    private static File file;
    private static YamlConfiguration config;

    private PendingNoteStorage() {
    }

    public static void init() {

        file = new File(SmartWithdraw.getInstance().getDataFolder(),
                "pending-notes.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                SmartWithdraw.getInstance().getLogger()
                        .warning("Could not create pending-notes.yml: "
                                + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    public static void addPendingNote(UUID uuid, String currencyId, int amount) {

        String key = uuid.toString();
        List<String> existing = config.getStringList(key);
        existing.add(currencyId + ":" + amount);
        config.set(key, existing);
        save();
    }

    /**
     * Delivers all pending notes to the player and removes them from storage.
     */
    public static void deliverPending(Player player) {

        String key = player.getUniqueId().toString();
        List<String> pending = config.getStringList(key);

        if (pending.isEmpty()) {
            return;
        }

        List<String> failed = new ArrayList<>();

        for (String entry : pending) {

            String[] parts = entry.split(":");

            if (parts.length != 2) {
                continue;
            }

            String currencyId = parts[0];
            int amount;

            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }

            Optional<Currency> currency = CurrencyManager.get(currencyId);

            if (currency.isEmpty()) {
                // Currency was removed from config since the note was stored
                // — skip silently rather than losing the entry
                failed.add(entry);
                continue;
            }

            InventoryUtils.give(player,
                    NoteFactory.createNote(currency.get(), amount));

            player.sendMessage("§6§lSmartWithdraw §8» §aYou received a pending "
                    + currency.get().format(amount) + " "
                    + SmartWithdraw.getInstance().getConfig()
                            .getString("notes.type-label", "Satchel")
                    + "§a from an admin.");
        }

        // Keep only failed entries (currency no longer exists)
        if (failed.isEmpty()) {
            config.set(key, null);
        } else {
            config.set(key, failed);
        }

        save();
    }

    public static boolean hasPending(UUID uuid) {
        return !config.getStringList(uuid.toString()).isEmpty();
    }

    private static void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            SmartWithdraw.getInstance().getLogger()
                    .warning("Could not save pending-notes.yml: "
                            + e.getMessage());
        }
    }
}
