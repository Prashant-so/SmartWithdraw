package com.smartwithdraw.balance;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Verified against the actual PlayerPoints source (org.black_ixx.playerpoints):
 * - PlayerPoints.getInstance().getAPI() returns the PlayerPointsAPI
 * - give(UUID, int) / take(UUID, int) / look(UUID) are the correct
 *   method names and signatures, confirmed directly from
 *   PlayerPointsAPI.java in the PlayerPoints-master source.
 */
public class PlayerPointsBalanceProvider implements BalanceProvider {

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

        if (!isAvailable()) {
            return false;
        }

        return api().look(player.getUniqueId()) >= amount;
    }

    @Override
    public void withdraw(Player player, long amount) {

        if (!isAvailable()) {
            return;
        }

        api().take(player.getUniqueId(), (int) amount);
    }

    @Override
    public void deposit(Player player, long amount) {

        if (!isAvailable()) {
            return;
        }

        api().give(player.getUniqueId(), (int) amount);
    }

    @Override
    public long getBalance(Player player) {
        return isAvailable() ? api().look(player.getUniqueId()) : 0;
    }
}
