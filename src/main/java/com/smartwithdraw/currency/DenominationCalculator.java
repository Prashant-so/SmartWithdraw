package com.smartwithdraw.currency;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DenominationCalculator {

    private static final int[] DENOMINATIONS = {
            2000,
            500,
            100,
            50,
            10,
            1
    };

    private DenominationCalculator() {
    }

    public static Map<Integer, Integer> calculate(int amount) {

        Map<Integer, Integer> notes = new LinkedHashMap<>();

        int remaining = amount;

        for (int denomination : DENOMINATIONS) {

            int count = remaining / denomination;

            if (count > 0) {
                notes.put(denomination, count);
                remaining %= denomination;
            }
        }

        return notes;
    }
}
