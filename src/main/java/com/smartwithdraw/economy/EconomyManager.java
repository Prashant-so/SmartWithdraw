package com.smartwithdraw.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyManager {

    private static Economy economy;

    private EconomyManager() {
    }

    public static boolean setupEconomy() {

        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (provider == null) {
            return false;
        }

        economy = provider.getProvider();
        return economy != null;
    }

    public static Economy getEconomy() {
        return economy;
    }
}
