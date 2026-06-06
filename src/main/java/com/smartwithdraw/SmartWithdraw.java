package com.smartwithdraw;

import com.smartwithdraw.command.WithdrawCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    @Override
    public void onEnable() {

        getLogger().info("SmartWithdraw enabled!");

        if (getCommand("withdraw") != null) {
            getCommand("withdraw").setExecutor(new WithdrawCommand());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("SmartWithdraw disabled!");
    }
}
