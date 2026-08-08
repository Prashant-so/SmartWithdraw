package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

/**
 * These keys are shown on the action bar instead of chat when the
 * sender is a player — keeps success feedback unobtrusive.
 */
public final class Lang {

    private static final Set<String> ACTION_BAR_KEYS = Set.of(
            "withdraw-success",
            "withdraw-success-taxed",
            "deposit-success",
            "deposit-success-taxed",
            "split-success"
    );

    private Lang() {
    }

    public static void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public static void send(CommandSender sender, String key,
                             Map<String, String> placeholders) {

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        String prefix = plugin.getConfig()
                .getString("messages.prefix", "&6&lSmartWithdraw &8» ");
        String raw = plugin.getConfig().getString("messages." + key);

        if (raw == null) {
            raw = "&c[Missing message: " + key + "]";
        }

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        String translated = ChatColor.translateAlternateColorCodes('&', raw);

        if (sender instanceof Player player && ACTION_BAR_KEYS.contains(key)) {
            ActionBarUtil.send(player, raw);
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix) + translated);
        }
    }
}
