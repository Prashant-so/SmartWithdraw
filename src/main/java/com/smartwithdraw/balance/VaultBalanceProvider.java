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
    public void withdraw(Player player, long amount) {
        if (isAvailable()) {
            EconomyManager.withdraw(player, amount);
        }
    }

    @Override
    public void deposit(Player player, long amount) {
        if (isAvailable()) {
            EconomyManager.deposit(player, amount);
        }
    }

    @Override
    public long getBalance(Player player) {
        return isAvailable() ? (long) EconomyManager.getEconomy().getBalance(player) : 0;
    }
}
