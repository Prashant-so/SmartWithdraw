package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small shared helpers for the clickable admin messages. */
public final class Chat {

    public static final String LINE = "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private Chat() {
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String prefix() {
        return color(SmartWithdraw.getInstance().getConfig()
                .getString("messages.prefix", "&6&lSmartWithdraw &8» "));
    }

    public static void say(CommandSender to, String message) {
        to.sendMessage(prefix() + message);
    }

    public static TextComponent button(String label, String hover,
                                       ClickEvent.Action action, String value) {
        TextComponent c = new TextComponent(TextComponent.fromLegacyText(label));
        c.setClickEvent(new ClickEvent(action, value));
        c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacyText(hover))));
        return c;
    }

    public static void sendRow(Player player, BaseComponent... parts) {
        player.spigot().sendMessage(new TextComponent(parts));
    }
}
