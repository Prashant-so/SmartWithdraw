package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Reads messages from config.yml's "messages" section, applies
 * %placeholder% substitutions, and sends them prefixed. Replaces the
 * old hardcoded Messages.java constants class - delete that file,
 * it's no longer used anywhere.
 */
public final class Lang {

    private Lang() {
    }

    public static void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public static void send(CommandSender sender, String key, Map<String, String> placeholders) {

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        String prefix = plugin.getConfig().getString("messages.prefix", "&6&lSmartWithdraw &8» ");
        String raw = plugin.getConfig().getString("messages." + key);

        if (raw == null) {
            raw = "&c[Missing message: " + key + "]";
        }

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + raw));
    }
}
