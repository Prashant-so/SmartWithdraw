package com.smartwithdraw.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownManager {

    private static final Map<UUID, Long> WITHDRAW_TIMESTAMPS = new HashMap<>();
    private static final Map<UUID, Long> DEPOSIT_TIMESTAMPS = new HashMap<>();

    private CooldownManager() {
    }

    public static long remainingWithdrawSeconds(Player player, int cooldownSeconds) {
        return remaining(WITHDRAW_TIMESTAMPS, player, cooldownSeconds);
    }

    public static long remainingDepositSeconds(Player player, int cooldownSeconds) {
        return remaining(DEPOSIT_TIMESTAMPS, player, cooldownSeconds);
    }

    public static void markWithdraw(Player player) {
        WITHDRAW_TIMESTAMPS.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public static void markDeposit(Player player) {
        DEPOSIT_TIMESTAMPS.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private static long remaining(Map<UUID, Long> timestamps, Player player, int cooldownSeconds) {

        if (cooldownSeconds <= 0) {
            return 0;
        }

        Long last = timestamps.get(player.getUniqueId());

        if (last == null) {
            return 0;
        }

        long elapsedSeconds = (System.currentTimeMillis() - last) / 1000L;
        long remaining = cooldownSeconds - elapsedSeconds;

        return Math.max(0, remaining);
    }
}
