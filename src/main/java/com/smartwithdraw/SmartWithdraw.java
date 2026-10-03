package com.smartwithdraw;

import com.smartwithdraw.command.DepositCommand;
import com.smartwithdraw.command.SmartWithdrawCommand;
import com.smartwithdraw.command.WithdrawCommand;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.health.HealthManager;
import com.smartwithdraw.listener.BankMenuListener;
import com.smartwithdraw.listener.NoteExpiryListener;
import com.smartwithdraw.listener.NoteRedeemListener;
import com.smartwithdraw.listener.NoteSplitListener;
import com.smartwithdraw.listener.PlatformListener;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.placeholder.SmartWithdrawExpansion;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.security.SecretKeyManager;
import com.smartwithdraw.storage.PendingNoteStorage;
import com.smartwithdraw.update.UpdateManager;
import com.smartwithdraw.util.CooldownManager;
import com.smartwithdraw.util.DailyLimitManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SmartWithdraw extends JavaPlugin {

    private static SmartWithdraw instance;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        // Vault/economy is no longer a hard requirement here. If it is
        // missing the plugin still loads, the affected currencies report
        // "backend unavailable", and HealthManager tells the admins why.
        EconomyManager.setupEconomy();

        SecretKeyManager.load();
        CurrencyManager.load();

        NoteValidator.init();
        NoteFactory.init();

        PendingNoteStorage.init();
        CooldownManager.init();
        DailyLimitManager.init();
        TransactionLogger.init();

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

        getServer().getPluginManager()
                .registerEvents(new NoteRedeemListener(), this);
        getServer().getPluginManager()
                .registerEvents(new NoteSplitListener(), this);
        getServer().getPluginManager()
                .registerEvents(new BankMenuListener(), this);
        getServer().getPluginManager()
                .registerEvents(new NoteExpiryListener(), this);
        getServer().getPluginManager()
                .registerEvents(new PlatformListener(), this);

        getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                PendingNoteStorage.deliverPending(event.getPlayer());
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                // Save this player's cooldown timestamps immediately
                // on logout so a crash between quit and next autosave
                // doesn't lose their cooldown state
                CooldownManager.save();
                DailyLimitManager.save();
            }

        }, this);

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

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SmartWithdrawExpansion().register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            getLogger().info("Hooked into PlayerPoints.");
        }

        // Feature 2: dependency health check (runs on the first server tick)
        HealthManager.init();
        // Feature 1: update checker (first check ~5s after start, then every N hours)
        UpdateManager.init();

        getLogger().info("SmartWithdraw enabled successfully.");
    }

    @Override
    public void onDisable() {
        UpdateManager.shutdown();
        CooldownManager.save();
        DailyLimitManager.save();
        TransactionLogger.close();
        getLogger().info("SmartWithdraw disabled.");
    }

    public static SmartWithdraw getInstance() {
        return instance;
    }

    /** The plugin's own jar file (getFile() is protected, so exposed here). */
    public File getJarFile() {
        return getFile();
    }
}
