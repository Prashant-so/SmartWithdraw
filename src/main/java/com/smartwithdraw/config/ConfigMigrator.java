package com.smartwithdraw.config;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Keeps an existing config.yml up to date after a plugin update.
 *
 *  - Options added in newer versions are appended to the admin's file,
 *    comments included. Existing values are never changed or removed.
 *  - The result is checked before anything is written; if anything looks
 *    wrong the file is left exactly as it was.
 *  - A backup is taken first (plugins/SmartWithdraw/backups/).
 *  - "config-version" records which layout a file has, so a future version
 *    that must rename or move an option can convert old files safely.
 *
 * Runs once at startup, before any other code reads the config.
 */
public final class ConfigMigrator {

    /** Raise this (and config-version in config.yml) only when an option is renamed or moved. */
    public static final int CURRENT_VERSION = 1;

    private static final String VERSION_KEY = "config-version";
    private static final int KEEP_BACKUPS = 5;

    /** Never touched: currencies are admin-defined, security holds the signing secret. */
    private static final Set<String> SKIP = Set.of("currencies", "security");

    private ConfigMigrator() {
    }

    public static void run(SmartWithdraw plugin) {
        try {
            migrate(plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("Config update check skipped: " + e.getMessage()
                    + " (your config.yml was not changed)");
        }
    }

    private static void migrate(SmartWithdraw plugin) throws Exception {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(file)) return;

        String serverText = Files.readString(file, StandardCharsets.UTF_8);
        String jarText;
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            jarText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        YamlConfiguration current = new YamlConfiguration();
        try {
            current.loadFromString(serverText);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().warning("config.yml has a YAML error, so it was not checked for new options.");
            return;
        }

        int from = current.getInt(VERSION_KEY, 0);
        if (from > CURRENT_VERSION) {
            plugin.getLogger().info("config.yml comes from a newer SmartWithdraw version; leaving it as it is.");
            return;
        }

        String base = upgrade(from, serverText);
        ConfigMerge.Result result = ConfigMerge.merge(base, jarText, current::contains,
                SKIP, VERSION_KEY, from, CURRENT_VERSION);
        if (!result.changed()) return;

        // Safety check: the new text must parse, keep every existing value, and contain the additions.
        YamlConfiguration updated = new YamlConfiguration();
        updated.loadFromString(result.text());
        for (String key : current.getKeys(true)) {
            if (current.isConfigurationSection(key) || key.equals(VERSION_KEY)) continue;
            if (!Objects.equals(current.get(key), updated.get(key))) {
                throw new IOException("an existing setting (" + key + ") would have changed");
            }
        }
        for (String path : result.added()) {
            if (!updated.contains(path)) throw new IOException("could not add " + path);
        }

        Path backups = plugin.getDataFolder().toPath().resolve("backups");
        Files.createDirectories(backups);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String backupName = "config-before-migrate-" + stamp + ".yml";
        Files.copy(file, backups.resolve(backupName), StandardCopyOption.REPLACE_EXISTING);

        Path tmp = file.resolveSibling("config.yml.tmp");
        Files.writeString(tmp, result.text(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        prune(backups);

        plugin.reloadConfig();
        report(plugin, result.added(), backupName);
    }

    /**
     * Future layout conversions go here, applied to the admin's text before new
     * options are added. Example for a later version:
     *   if (from < 2) text = ...convert the old layout...;
     * Today there is nothing to convert (layout version 1 is the first).
     */
    private static String upgrade(int from, String text) {
        return text;
    }

    private static void report(SmartWithdraw plugin, List<String> added, String backupName) {
        if (added.isEmpty()) return;
        int shown = Math.min(added.size(), 6);
        String names = String.join(", ", added.subList(0, shown))
                + (added.size() > shown ? ", +" + (added.size() - shown) + " more" : "");
        plugin.getLogger().info("config.yml updated: added " + added.size()
                + " new option(s): " + names);
        plugin.getLogger().info("Your existing settings were not changed. Backup: plugins/"
                + plugin.getDataFolder().getName() + "/backups/" + backupName);
    }

    private static void prune(Path backups) {
        try (Stream<Path> files = Files.list(backups)) {
            List<Path> old = files
                    .filter(p -> p.getFileName().toString().startsWith("config-before-migrate-"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (int i = KEEP_BACKUPS; i < old.size(); i++) Files.deleteIfExists(old.get(i));
        } catch (IOException ignored) {
            // pruning is best-effort
        }
    }
}
