package com.smartwithdraw;

import com.smartwithdraw.command.DepositCommand;
import com.smartwithdraw.command.SmartWithdrawCommand;
import com.smartwithdraw.command.WithdrawCommand;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.listener.BankMenuListener;
import com.smartwithdraw.listener.NoteRedeemListener;
import com.smartwithdraw.listener.NoteSplitListener;
import com.smartwithdraw.listener.ResourcePackListener;
import com.smartwithdraw.placeholder.SmartWithdrawExpansion;
import com.smartwithdraw.security.SecretKeyManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    private static SmartWithdraw instance;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        if (!EconomyManager.setupEconomy()) {
            getLogger().severe("Vault not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SecretKeyManager.load();
        CurrencyManager.load();

        if (getCommand("withdraw") != null) {
            getCommand("withdraw").setExecutor(new WithdrawCommand());
        }

        if (getCommand("deposit") != null) {
            getCommand("deposit").setExecutor(new DepositCommand());
        }

        if (getCommand("smartwithdraw") != null) {
            getCommand("smartwithdraw").setExecutor(new SmartWithdrawCommand());
        }

        getServer().getPluginManager().registerEvents(new NoteRedeemListener(), this);
        getServer().getPluginManager().registerEvents(new NoteSplitListener(), this);
        getServer().getPluginManager().registerEvents(new BankMenuListener(), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SmartWithdrawExpansion().register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            getLogger().info("Hooked into PlayerPoints.");
        }

        getLogger().info("SmartWithdraw enabled.");
    }

    @Override
    public void onDisable() {
    }

    public static SmartWithdraw getInstance() {
        return instance;
    }
}
