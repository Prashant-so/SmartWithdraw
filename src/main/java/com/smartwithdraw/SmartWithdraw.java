package com.smartwithdraw;

import com.smartwithdraw.command.DepositCommand;
import com.smartwithdraw.command.SmartWithdrawCommand;
import com.smartwithdraw.command.WithdrawCommand;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.listener.NoteRedeemListener;
import com.smartwithdraw.listener.ResourcePackListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    private static SmartWithdraw instance;

    @Override
    public void onEnable() {

        instance = this;

        if (!EconomyManager.setupEconomy()) {
            getLogger().severe("Vault not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("withdraw") != null) {
            getCommand("withdraw").setExecutor(new WithdrawCommand());
        }

        if (getCommand("deposit") != null) {
            getCommand("deposit").setExecutor(new DepositCommand());
        }

        if (getCommand("smartwithdraw") != null) {
            getCommand("smartwithdraw").setExecutor(new SmartWithdrawCommand());
        }

        getServer().getPluginManager().registerEvents(
                new NoteRedeemListener(),
                this
        );

        getServer().getPluginManager().registerEvents(
                new ResourcePackListener(),
                this
        );

        saveDefaultConfig();

        getLogger().info("SmartWithdraw enabled.");
    }

    @Override
    public void onDisable() {
    }

    public static SmartWithdraw getInstance() {
        return instance;
    }
}
