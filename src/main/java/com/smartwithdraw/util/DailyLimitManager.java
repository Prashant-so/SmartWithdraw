package com.smartwithdraw.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-player per-currency withdrawn amounts
 * within a rolling 24-hour window.
 * In-memory only — resets on server restart, which is acceptable
 * since the window is rolling and short-lived anyway.
 */
public final class DailyLimitManager {

    // key: playerUUID + ":" + currencyId
    private static final Map<String, Long> WITHDRAWN  = new HashMap<>();
    private static final Map<String, Long> WINDOW_START = new HashMap<>();

    private DailyLimitManager() {
    }

    private static String key(UUID uuid, String currencyId) {
        return uuid + ":" + currencyId.toLowerCase();
    }

    /**
     * Returns how much the player has withdrawn for this currency
     * in the current 24hr window.
     */
    public static long getWithdrawn(UUID uuid, String currencyId) {
        String k = key(uuid, currencyId);
        purgeIfExpired(k);
        return WITHDRAWN.getOrDefault(k, 0L);
    }

    /**
     * Returns how many milliseconds until the player's window resets.
     */
    public static long msUntilReset(UUID uuid, String currencyId) {
        String k = key(uuid, currencyId);
        Long start = WINDOW_START.get(k);
        if (start == null) return 0;
        long elapsed = System.currentTimeMillis() - start;
        long remaining = 86_400_000L - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Returns a human-readable reset time string e.g. "5h 23m".
     */
    public static String resetString(UUID uuid, String currencyId) {
        long ms = msUntilReset(uuid, currencyId);
        if (ms <= 0) return "now";
        long totalSeconds = ms / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    /**
     * Records a withdrawal. Returns false if it would exceed the limit.
     */
    public static boolean tryWithdraw(UUID uuid, String currencyId,
                                       long amount, long limit) {
        if (limit <= 0) return true; // unlimited

        String k = key(uuid, currencyId);
        purgeIfExpired(k);

        long current = WITHDRAWN.getOrDefault(k, 0L);

        if (current + amount > limit) return false;

        WITHDRAWN.put(k, current + amount);
        WINDOW_START.putIfAbsent(k, System.currentTimeMillis());

        return true;
    }

    private static void purgeIfExpired(String k) {
        Long start = WINDOW_START.get(k);
        if (start == null) return;
        if (System.currentTimeMillis() - start >= 86_400_000L) {
            WITHDRAWN.remove(k);
            WINDOW_START.remove(k);
        }
    }
}
