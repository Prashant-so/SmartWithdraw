package com.smartwithdraw;

import com.smartwithdraw.command.WithdrawCommand;
import com.smartwithdraw.listener.NoteRedeemListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    @Override
    public void onEnable() {

        getLogger().info("SmartWithdraw enabled!");

        if (getCommand("withdraw") != null) {
            getCommand("withdraw").setExecutor(new WithdrawCommand());
        }

        getServer().getPluginManager().registerEvents(
                new NoteRedeemListener(),
                this
        );
    }

    @Override
    public void onDisable() {
        getLogger().info("SmartWithdraw disabled!");
    }
}
