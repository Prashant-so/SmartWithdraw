package com.smartwithdraw;

import com.smartwithdraw.command.WithdrawCommand;
import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    private static SmartWithdraw instance;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        if (!EconomyManager.setup(this)) {
            getLogger().severe("Failed to hook into Vault!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("withdraw") != null) {
            getCommand("withdraw").setExecutor(new WithdrawCommand());
        }

        getLogger().info("SmartWithdraw enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SmartWithdraw disabled!");
    }

    public static SmartWithdraw getInstance() {
        return instance;
    }
}
