package com.smartwithdraw.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyManager {

    private static Economy economy;

    public static boolean setup(JavaPlugin plugin) {

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault not found!");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().severe("No Economy Provider Found!");
            return false;
        }

        economy = rsp.getProvider();
        plugin.getLogger().info("Vault hooked successfully.");
        return true;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public static double getBalance(Player player) {
        return economy.getBalance(player);
    }

    public static boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public static boolean deposit(Player player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
