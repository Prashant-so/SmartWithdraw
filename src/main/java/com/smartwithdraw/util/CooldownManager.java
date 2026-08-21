package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownManager {

    private static final Map<UUID, Long> WITHDRAW_TIMESTAMPS = new HashMap<>();
    private static final Map<UUID, Long> DEPOSIT_TIMESTAMPS  = new HashMap<>();

    private static File file;
    private static YamlConfiguration config;

    private CooldownManager() {
    }

    public static void init() {

        file = new File(SmartWithdraw.getInstance().getDataFolder(), "cooldowns.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                SmartWithdraw.getInstance().getLogger()
                        .warning("Could not create cooldowns.yml: " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(file);

        long now = System.currentTimeMillis();

        if (config.isConfigurationSection("withdraw")) {
            for (String key : config.getConfigurationSection("withdraw").getKeys(false)) {
                long ts = config.getLong("withdraw." + key);
                if (now - ts < 86_400_000L) {
                    WITHDRAW_TIMESTAMPS.put(UUID.fromString(key), ts);
                }
            }
        }

        if (config.isConfigurationSection("deposit")) {
            for (String key : config.getConfigurationSection("deposit").getKeys(false)) {
                long ts = config.getLong("deposit." + key);
                if (now - ts < 86_400_000L) {
                    DEPOSIT_TIMESTAMPS.put(UUID.fromString(key), ts);
                }
            }
        }
    }

    public static long remainingWithdrawSeconds(Player player, int cooldownSeconds) {
        return remaining(WITHDRAW_TIMESTAMPS, player, cooldownSeconds);
    }

    public static long remainingDepositSeconds(Player player, int cooldownSeconds) {
        return remaining(DEPOSIT_TIMESTAMPS, player, cooldownSeconds);
    }

    public static void markWithdraw(Player player) {
        long now = System.currentTimeMillis();
        WITHDRAW_TIMESTAMPS.put(player.getUniqueId(), now);
        config.set("withdraw." + player.getUniqueId(), now);
        save();
    }

    public static void markDeposit(Player player) {
        long now = System.currentTimeMillis();
        DEPOSIT_TIMESTAMPS.put(player.getUniqueId(), now);
        config.set("deposit." + player.getUniqueId(), now);
        save();
    }

    public static void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            SmartWithdraw.getInstance().getLogger()
                    .warning("Could not save cooldowns.yml: " + e.getMessage());
        }
    }

    private static long remaining(Map<UUID, Long> timestamps,
                                   Player player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        Long last = timestamps.get(player.getUniqueId());
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldownSeconds - elapsed);
    }
}
