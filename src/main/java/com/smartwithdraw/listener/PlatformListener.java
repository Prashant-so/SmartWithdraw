package com.smartwithdraw.listener;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.health.HealthManager;
import com.smartwithdraw.update.UpdateManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Set;

/** Join notices for admins + re-checks when dependencies come or go. */
public class PlatformListener implements Listener {

    private static final Set<String> WATCHED =
            Set.of("Vault", "Essentials", "PlayerPoints", "PlaceholderAPI");

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Cheap permission check first - normal players cost nothing
        if (!player.hasPermission(HealthManager.PERMISSION)
                && !player.hasPermission(UpdateManager.PERMISSION)) {
            return;
        }

        // 3 seconds later, so it appears after the MOTD / other join spam
        Bukkit.getScheduler().runTaskLater(SmartWithdraw.getInstance(), () -> {
            if (!player.isOnline()) return;
            HealthManager.notifyAdmin(player);   // problems first
            UpdateManager.notifyAdmin(player);   // then updates
        }, 60L);
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (isEconomy(event.getProvider())) HealthManager.scheduleRefresh();
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (isEconomy(event.getProvider())) HealthManager.scheduleRefresh();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (WATCHED.contains(event.getPlugin().getName())) HealthManager.scheduleRefresh();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (WATCHED.contains(event.getPlugin().getName())) HealthManager.scheduleRefresh();
    }

    // Compared by name on purpose: touching Economy.class would crash
    // when Vault is not installed.
    private static boolean isEconomy(RegisteredServiceProvider<?> provider) {
        return provider != null
                && "net.milkbowl.vault.economy.Economy".equals(provider.getService().getName());
    }
}
