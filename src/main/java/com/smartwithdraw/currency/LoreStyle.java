package com.smartwithdraw.currency;

public record LoreStyle(
        String titleColor,
        boolean showTier,
        String tier,
        String gainText,
        double taxPercent
) {
}
