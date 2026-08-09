package com.smartwithdraw.balance;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerPointsBalanceProvider implements BalanceProvider {

    // Capped at Integer.MAX_VALUE since PlayerPoints API takes int,
    // not long. Values above this are silently capped to prevent
    // integer overflow / wrong amounts being credited or deducted.
    private static final long PP_MAX = Integer.MAX_VALUE;

    private PlayerPointsAPI api;

    private PlayerPointsAPI api() {
        if (api == null && isAvailable()) {
            api = PlayerPoints.getInstance().getAPI();
        }
        return api;
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PlayerPoints") != null;
    }

    @Override
    public boolean has(Player player, long amount) {
        if (!isAvailable()) return false;
        // If amount exceeds PP_MAX, player can never have enough
        if (amount > PP_MAX) return false;
        return api().look(player.getUniqueId()) >= (int) amount;
    }

    @Override
    public void withdraw(Player player, long amount) {
        if (!isAvailable()) return;
        int safeAmount = (int) Math.min(amount, PP_MAX);
        api().take(player.getUniqueId(), safeAmount);
    }

    @Override
    public void deposit(Player player, long amount) {
        if (!isAvailable()) return;
        int safeAmount = (int) Math.min(amount, PP_MAX);
        api().give(player.getUniqueId(), safeAmount);
    }

    @Override
    public long getBalance(Player player) {
        return isAvailable() ? api().look(player.getUniqueId()) : 0;
    }
}
