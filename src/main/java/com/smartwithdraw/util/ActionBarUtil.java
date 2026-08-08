package com.smartwithdraw.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class ActionBarUtil {

    private ActionBarUtil() {
    }

    public static void send(Player player, String message) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.translateAlternateColorCodes('&', message))
        );
    }
}
