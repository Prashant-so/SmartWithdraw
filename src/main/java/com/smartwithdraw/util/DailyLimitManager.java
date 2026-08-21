package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DailyLimitManager {

    private static final Map<String, Long> WITHDRAWN    = new HashMap<>();
    private static final Map<String, Long> WINDOW_START = new HashMap<>();

    private static File file;
    private static YamlConfiguration config;

    private DailyLimitManager() {
    }

    public static void init() {

        file = new File(SmartWithdraw.getInstance().getDataFolder(),
                "daily-limits.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                SmartWithdraw.getInstance().getLogger()
                        .warning("Could not create daily-limits.yml: "
                                + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(file);

        long now = System.currentTimeMillis();

        if (config.isConfigurationSection("windows")) {
            for (String k : config.getConfigurationSection("windows").getKeys(false)) {
                long start = config.getLong("windows." + k);
                if (now - start < 86_400_000L) {
                    WINDOW_START.put(k, start);
                    WITHDRAWN.put(k, config.getLong("withdrawn." + k, 0L));
                }
            }
        }
    }

    private static String key(UUID uuid, String currencyId) {
        return uuid + ":" + currencyId.toLowerCase();
    }

    public static long getWithdrawn(UUID uuid, String currencyId) {
        String k = key(uuid, currencyId);
        purgeIfExpired(k);
        return WITHDRAWN.getOrDefault(k, 0L);
    }

    public static long msUntilReset(UUID uuid, String currencyId) {
        String k = key(uuid, currencyId);
        Long start = WINDOW_START.get(k);
        if (start == null) return 0;
        long remaining = 86_400_000L - (System.currentTimeMillis() - start);
        return Math.max(0, remaining);
    }

    public static String resetString(UUID uuid, String currencyId) {
        long ms = msUntilReset(uuid, currencyId);
        if (ms <= 0) return "now";
        long totalSeconds = ms / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    public static boolean tryWithdraw(UUID uuid, String currencyId,
                                       long amount, long limit) {
        if (limit <= 0) return true;

        String k = key(uuid, currencyId);
        purgeIfExpired(k);

        long current = WITHDRAWN.getOrDefault(k, 0L);
        if (current + amount > limit) return false;

        long newTotal = current + amount;
        WITHDRAWN.put(k, newTotal);
        WINDOW_START.putIfAbsent(k, System.currentTimeMillis());

        config.set("withdrawn." + k, newTotal);
        config.set("windows." + k, WINDOW_START.get(k));
        save();

        return true;
    }

    public static void refund(UUID uuid, String currencyId, long amount) {
        String k = key(uuid, currencyId);
        long current = WITHDRAWN.getOrDefault(k, 0L);
        long newAmount = Math.max(0, current - amount);
        if (newAmount == 0) {
            WITHDRAWN.remove(k);
            WINDOW_START.remove(k);
            config.set("withdrawn." + k, null);
            config.set("windows." + k, null);
        } else {
            WITHDRAWN.put(k, newAmount);
            config.set("withdrawn." + k, newAmount);
        }
        save();
    }

    public static void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            SmartWithdraw.getInstance().getLogger()
                    .warning("Could not save daily-limits.yml: " + e.getMessage());
        }
    }

    private static void purgeIfExpired(String k) {
        Long start = WINDOW_START.get(k);
        if (start == null) return;
        if (System.currentTimeMillis() - start >= 86_400_000L) {
            WITHDRAWN.remove(k);
            WINDOW_START.remove(k);
            config.set("withdrawn." + k, null);
            config.set("windows." + k, null);
            save();
        }
    }
}
