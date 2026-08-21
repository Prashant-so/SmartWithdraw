package com.smartwithdraw.balance;

import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.entity.Player;

public class VaultBalanceProvider implements BalanceProvider {

    @Override
    public boolean isAvailable() {
        return EconomyManager.getEconomy() != null;
    }

    @Override
    public boolean has(Player player, long amount) {
        return isAvailable() && EconomyManager.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, long amount) {
        if (!isAvailable()) return false;
        return EconomyManager.withdraw(player, amount);
    }

    @Override
    public boolean deposit(Player player, long amount) {
        if (!isAvailable()) return false;
        return EconomyManager.deposit(player, amount);
    }

    @Override
    public long getBalance(Player player) {
        return isAvailable() ? (long) EconomyManager.getEconomy().getBalance(player) : 0;
    }
}
