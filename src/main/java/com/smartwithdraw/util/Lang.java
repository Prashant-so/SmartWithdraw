package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public final class Lang {

    // These keys are sent to action bar instead of chat when
    // the sender is a player — keeps success feedback unobtrusive
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

        if (sender instanceof Player player && ACTION_BAR_KEYS.contains(key)) {
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent(
                            ChatColor.translateAlternateColorCodes('&', raw)));
        } else {
            String translated = ChatColor.translateAlternateColorCodes('&',
                    prefix + raw);
            sender.sendMessage(translated);
        }
    }
}
