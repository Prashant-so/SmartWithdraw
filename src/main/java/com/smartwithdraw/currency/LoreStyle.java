package com.smartwithdraw.currency;

import java.util.List;

public record LoreStyle(
        String titleColor,
        boolean showTier,
        String tier,
        String gainText,
        List<String> template
) {
}
