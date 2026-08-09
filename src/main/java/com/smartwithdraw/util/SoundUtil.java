package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private SoundUtil() {
    }

    public static void play(Player player, String configKey) {

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }

        String soundName = plugin.getConfig()
                .getString("sounds." + configKey, "");

        if (soundName == null || soundName.isBlank()) return;

        try {
            Sound sound  = Sound.valueOf(soundName.toUpperCase());
            float volume = (float) plugin.getConfig().getDouble("sounds.volume", 1.0);
            float pitch  = (float) plugin.getConfig().getDouble("sounds.pitch", 1.0);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "Invalid sound '" + soundName + "' in sounds." + configKey);
        }
    }
}
