package com.smartwithdraw.balance;

import com.smartwithdraw.util.ExperienceUtils;
import org.bukkit.entity.Player;

public class XpBalanceProvider implements BalanceProvider {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean has(Player player, long amount) {
        return ExperienceUtils.getTotalExperience(player) >= amount;
    }

    @Override
    public boolean withdraw(Player player, long amount) {
        long current = ExperienceUtils.getTotalExperience(player);
        if (current < amount) return false;
        ExperienceUtils.setTotalExperience(player, current - amount);
        return true;
    }

    @Override
    public boolean deposit(Player player, long amount) {
        long current = ExperienceUtils.getTotalExperience(player);
        ExperienceUtils.setTotalExperience(player, current + amount);
        return true;
    }

    @Override
    public long getBalance(Player player) {
        return ExperienceUtils.getTotalExperience(player);
    }
}
