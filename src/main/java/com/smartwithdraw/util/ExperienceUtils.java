package com.smartwithdraw.util;

import org.bukkit.entity.Player;

/**
 * Total-experience-point math. Bukkit's Player#getTotalExperience()
 * is unreliable (it doesn't decrease when XP is spent, e.g. on
 * enchanting), so this recomputes true total XP from level + progress
 * using the vanilla level-cost formulas, and can set an exact total
 * back onto a player.
 */
public final class ExperienceUtils {

    private ExperienceUtils() {
    }

    public static int getExpAtLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }

    public static long getTotalExperience(Player player) {

        int level = player.getLevel();
        long exp = Math.round(getExpAtLevel(level) * player.getExp());

        if (level <= 15) {
            exp += (long) level * level + 6L * level;
        } else if (level <= 30) {
            exp += (long) (2.5 * level * level - 40.5 * level + 360);
        } else {
            exp += (long) (4.5 * level * level - 162.5 * level + 2220);
        }

        return exp;
    }

    /**
     * Resets the player to 0 XP/level and re-adds the given total using
     * Bukkit's giveExp (which handles level-ups correctly), piece by piece.
     */
    public static void setTotalExperience(Player player, long total) {

        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);

        long remaining = total;

        while (remaining > 0) {

            int atLevel = getExpAtLevel(player.getLevel());

            if (remaining >= atLevel) {
                player.giveExp(atLevel);
                remaining -= atLevel;
            } else {
                player.giveExp((int) remaining);
                remaining = 0;
            }
        }
    }
}
