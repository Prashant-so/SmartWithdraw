package com.smartwithdraw.currency;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DenominationCalculator {

    private DenominationCalculator() {
    }

    public static Map<Integer, Integer> calculate(int amount, Currency currency) {

        Map<Integer, Integer> notes = new LinkedHashMap<>();

        List<Integer> denominations = currency.denominations()
                .stream().sorted((a, b) -> b - a).toList();

        int remaining = amount;

        for (int denomination : denominations) {
            if (denomination <= 0) continue;
            int count = remaining / denomination;
            if (count > 0) {
                notes.merge(denomination, count, Integer::sum);
                remaining %= denomination;
            }
        }

        if (remaining > 0) notes.merge(remaining, 1, Integer::sum);

        return notes;
    }

    public static Map<Integer, Integer> splitDown(int value, Currency currency) {

        List<Integer> smaller = currency.denominations().stream()
                .filter(d -> d < value).sorted((a, b) -> b - a).toList();

        if (smaller.isEmpty()) return Map.of();

        return calculate(value, currencyWithout(currency, value));
    }

    private static Currency currencyWithout(Currency currency, int excludedValue) {

        List<Integer> filtered = currency.denominations().stream()
                .filter(d -> d != excludedValue).toList();

        return new Currency(
                currency.id(), currency.symbol(), currency.name(),
                currency.namePlural(), currency.format(), filtered,
                currency.customModelDataBase(), currency.isDefault(),
                currency.enabled(), currency.backend(), currency.material(),
                currency.expiryDays(), currency.dailyLimit(),
                currency.allowedWorlds(), currency.tax(), currency.lore()
        );
    }
}
