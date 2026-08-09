package com.smartwithdraw.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DailyLimitManager {

    private static final Map<String, Long> WITHDRAWN    = new HashMap<>();
    private static final Map<String, Long> WINDOW_START = new HashMap<>();

    private DailyLimitManager() {
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

        WITHDRAWN.put(k, current + amount);
        WINDOW_START.putIfAbsent(k, System.currentTimeMillis());
        return true;
    }

    /**
     * Refunds a previously recorded withdrawal — called when a
     * balance check or inventory check fails AFTER the limit was
     * already reserved, so the player's daily allowance isn't
     * permanently consumed by a failed attempt.
     */
    public static void refund(UUID uuid, String currencyId, long amount) {
        String k = key(uuid, currencyId);
        long current = WITHDRAWN.getOrDefault(k, 0L);
        long newAmount = Math.max(0, current - amount);
        if (newAmount == 0) {
            WITHDRAWN.remove(k);
            WINDOW_START.remove(k);
        } else {
            WITHDRAWN.put(k, newAmount);
        }
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
