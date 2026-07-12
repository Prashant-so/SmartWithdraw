package com.smartwithdraw.currency;

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
}
