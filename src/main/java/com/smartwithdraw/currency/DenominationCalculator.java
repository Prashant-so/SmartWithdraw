package com.smartwithdraw.currency;

import java.util.LinkedHashMap;
import java.util.Map;

public class DenominationCalculator {

    private static final int[] DENOMINATIONS = {
            2000,
            500,
            100,
            50,
            10,
            1
    };

    public static Map<Integer, Integer> calculate(int amount) {

        Map<Integer, Integer> notes = new LinkedHashMap<>();

        for (int value : DENOMINATIONS) {

            int count = amount / value;

            if (count > 0) {
                notes.put(value, count);
                amount %= value;
            }
        }

        return notes;
    }
}
