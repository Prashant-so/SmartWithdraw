package com.smartwithdraw.currency;

public record TaxConfig(
        double percent,
        boolean applyOnWithdraw,
        boolean applyOnDeposit
) {

    public static final TaxConfig NONE = new TaxConfig(0.0, false, false);

    public TaxConfig {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
    }

    public long calculateTax(long value) {
        if (percent <= 0) return 0;
        return (long) Math.floor(value * percent / 100.0);
    }

    public long applyTax(long value) {
        return Math.max(0, value - calculateTax(value));
    }

    public String displayString() {
        if (percent <= 0) return "0.0%";
        if (applyOnWithdraw && applyOnDeposit) return percent + "% (withdraw & deposit)";
        if (applyOnWithdraw) return percent + "% (on withdraw)";
        if (applyOnDeposit)  return percent + "% (on deposit)";
        return percent + "% (inactive)";
    }
}
