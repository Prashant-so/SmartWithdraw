package com.smartwithdraw.currency;

import org.bukkit.Material;

import java.util.List;

public record Currency(
        String id,
        String symbol,
        String name,
        String namePlural,
        String format,
        List<Integer> denominations,
        int customModelDataBase,
        boolean isDefault,
        boolean enabled,
        CurrencyBackend backend,
        Material material,
        int expiryDays,
        long dailyLimit,
        List<String> allowedWorlds,
        TaxConfig tax,
        LoreStyle lore
) {

    public String getDisplayName(long amount) {
        return amount == 1 ? name : namePlural;
    }

    public String format(long amount) {
        return format
                .replace("%symbol%", symbol)
                .replace("%amount%", Long.toString(amount))
                .replace("%name%", getDisplayName(amount));
    }

    public boolean isWorldAllowed(String worldName) {
        if (allowedWorlds == null || allowedWorlds.isEmpty()) return true;
        return allowedWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(worldName));
    }

    public boolean hasExpiry() {
        return expiryDays > 0;
    }

    public boolean hasDailyLimit() {
        return dailyLimit > 0;
    }
}
