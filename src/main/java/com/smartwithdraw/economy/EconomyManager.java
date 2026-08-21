package com.smartwithdraw.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyManager {

    private static Economy economy;

    private EconomyManager() {
    }

    public static boolean setupEconomy() {
        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) return false;
        economy = provider.getProvider();
        return economy != null;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public static boolean has(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    public static boolean withdraw(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            Bukkit.getLogger().warning(
                    "[SmartWithdraw] Vault withdraw failed for " + player.getName()
                    + ": " + response.errorMessage);
        }
        return response.transactionSuccess();
    }

    public static boolean deposit(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            Bukkit.getLogger().warning(
                    "[SmartWithdraw] Vault deposit failed for " + player.getName()
                    + ": " + response.errorMessage);
        }
        return response.transactionSuccess();
    }
}
