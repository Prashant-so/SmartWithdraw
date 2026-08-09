package com.smartwithdraw;

import com.smartwithdraw.command.DepositCommand;
import com.smartwithdraw.command.SmartWithdrawCommand;
import com.smartwithdraw.command.WithdrawCommand;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.listener.BankMenuListener;
import com.smartwithdraw.listener.NoteExpiryListener;
import com.smartwithdraw.listener.NoteRedeemListener;
import com.smartwithdraw.listener.NoteSplitListener;
import com.smartwithdraw.listener.ResourcePackListener;
import com.smartwithdraw.placeholder.SmartWithdrawExpansion;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.security.SecretKeyManager;
import com.smartwithdraw.storage.PendingNoteStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmartWithdraw extends JavaPlugin {

    private static SmartWithdraw instance;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        if (!EconomyManager.setupEconomy()) {
            getLogger().severe(
                    "Vault not found or no economy plugin installed! Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SecretKeyManager.load();
        CurrencyManager.load();

        // Init static NamespacedKeys once — avoids creating them
        // on every note creation/validation call
        NoteValidator.init();
        NoteFactory.init();

        PendingNoteStorage.init();

        // Commands + tab completers
        if (getCommand("withdraw") != null) {
            WithdrawCommand wc = new WithdrawCommand();
            getCommand("withdraw").setExecutor(wc);
            getCommand("withdraw").setTabCompleter(wc);
        }

        if (getCommand("deposit") != null) {
            getCommand("deposit").setExecutor(new DepositCommand());
        }

        if (getCommand("smartwithdraw") != null) {
            SmartWithdrawCommand sc = new SmartWithdrawCommand();
            getCommand("smartwithdraw").setExecutor(sc);
            getCommand("smartwithdraw").setTabCompleter(sc);
        }

        // Listeners
        getServer().getPluginManager()
                .registerEvents(new NoteRedeemListener(), this);
        getServer().getPluginManager()
                .registerEvents(new NoteSplitListener(), this);
        getServer().getPluginManager()
                .registerEvents(new BankMenuListener(), this);
        getServer().getPluginManager()
                .registerEvents(new NoteExpiryListener(), this);
        getServer().getPluginManager()
                .registerEvents(new ResourcePackListener(), this);

        // Deliver pending notes on join
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                PendingNoteStorage.deliverPending(event.getPlayer());
            }
        }, this);

        // Periodic expiry scan
        int scanInterval = getConfig()
                .getInt("expiry.scan-interval-seconds", 30);

        if (scanInterval > 0) {
            long ticks = scanInterval * 20L;
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    NoteExpiryListener.scanAndDestroy(player);
                }
            }, ticks, ticks);
        }

        // Soft dependency hooks
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SmartWithdrawExpansion().register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            getLogger().info("Hooked into PlayerPoints.");
        }

        getLogger().info("SmartWithdraw 2.0 enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SmartWithdraw disabled.");
    }

    public static SmartWithdraw getInstance() {
        return instance;
    }
}
