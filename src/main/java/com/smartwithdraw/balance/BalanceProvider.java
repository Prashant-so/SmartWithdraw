package com.smartwithdraw.balance;

import org.bukkit.entity.Player;

public interface BalanceProvider {

    boolean isAvailable();

    boolean has(Player player, long amount);

    boolean withdraw(Player player, long amount);

    boolean deposit(Player player, long amount);

    long getBalance(Player player);
}
