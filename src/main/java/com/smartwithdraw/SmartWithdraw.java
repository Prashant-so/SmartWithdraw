package com.smartwithdraw;

import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("SmartWithdraw enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SmartWithdraw disabled!");
    }
}
