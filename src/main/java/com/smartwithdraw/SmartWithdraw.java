package com.smartwithdraw;

import com.smartwithdraw.command.WithdrawCommand;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.listener.NoteRedeemListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    @Override
    public void onEnable() {

        if (!EconomyManager.setupEconomy()) {
            getLogger().severe("Vault not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("withdraw") != null) {
            getCommand("withdraw").setExecutor(new WithdrawCommand());
        }

        getServer().getPluginManager().registerEvents(
                new NoteRedeemListener(),
                this
        );

        getLogger().info("SmartWithdraw enabled.");
    }

    @Override
    public void onDisable() {
    }
}
