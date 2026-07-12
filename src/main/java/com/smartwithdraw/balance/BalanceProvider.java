package com.smartwithdraw.balance;

import org.bukkit.entity.Player;

public interface BalanceProvider {

    boolean isAvailable();

    boolean has(Player player, long amount);

    void withdraw(Player player, long amount);

    void deposit(Player player, long amount);

    long getBalance(Player player);
}
