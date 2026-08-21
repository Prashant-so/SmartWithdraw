package com.smartwithdraw.balance;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerPointsBalanceProvider implements BalanceProvider {

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
        if (amount > PP_MAX) return false;
        return api().look(player.getUniqueId()) >= (int) amount;
    }

    @Override
    public boolean withdraw(Player player, long amount) {
        if (!isAvailable()) return false;
        if (amount > PP_MAX) return false;
        return api().take(player.getUniqueId(), (int) amount);
    }

    @Override
    public boolean deposit(Player player, long amount) {
        if (!isAvailable()) return false;
        int safeAmount = (int) Math.min(amount, PP_MAX);
        return api().give(player.getUniqueId(), safeAmount);
    }

    @Override
    public long getBalance(Player player) {
        return isAvailable() ? api().look(player.getUniqueId()) : 0;
    }
}
