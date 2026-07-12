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
    public void withdraw(Player player, long amount) {
        long current = ExperienceUtils.getTotalExperience(player);
        ExperienceUtils.setTotalExperience(player, Math.max(0, current - amount));
    }

    @Override
    public void deposit(Player player, long amount) {
        long current = ExperienceUtils.getTotalExperience(player);
        ExperienceUtils.setTotalExperience(player, current + amount);
    }

    @Override
    public long getBalance(Player player) {
        return ExperienceUtils.getTotalExperience(player);
    }
}
