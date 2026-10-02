package com.smartwithdraw.util;

import com.smartwithdraw.SmartWithdraw;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats short-hand amounts such as 1k, 1.5m, 2b.
 *
 * Parsing is deliberately strict (digits, one optional decimal part and one
 * suffix only) and uses exact BigDecimal arithmetic. There is no floating
 * point, so no rounding, NaN, Infinity or exponent ("1e9") tricks. Anything
 * that is not a whole positive number that fits in an int is rejected, which
 * keeps the result compatible with the int value signed into every note.
 *
 * Config (all optional, defaults shown):
 *   number-format.shorthand-input:   true  -> /withdraw 1k, 2.5m
 *   number-format.shorthand-display: true  -> lore / messages / GUI show 1.5m
 *   number-format.max-decimals:      2     -> 0-3, display is truncated (never rounded up)
 */
public final class AmountUtil {

    private static final Pattern PLAIN = Pattern.compile("\\d{1,10}");
    private static final Pattern SHORT = Pattern.compile(
            "(\\d{1,10}(?:\\.\\d{1,9})?)([kmbt])", Pattern.CASE_INSENSITIVE);

    private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);

    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    private static final long B = 1_000_000_000L;
    private static final long T = 1_000_000_000_000L;

    private AmountUtil() {
    }

    // ── Config-aware entry points ────────────────────────────────────

    public static OptionalInt parse(String input) {
        return parse(input, isShorthandInputEnabled());
    }

    public static String format(long amount) {
        return format(amount, isShorthandDisplayEnabled(), maxDecimals());
    }

    public static boolean isShorthandInputEnabled() {
        return SmartWithdraw.getInstance().getConfig()
                .getBoolean("number-format.shorthand-input", true);
    }

    public static boolean isShorthandDisplayEnabled() {
        return SmartWithdraw.getInstance().getConfig()
                .getBoolean("number-format.shorthand-display", true);
    }

    private static int maxDecimals() {
        return SmartWithdraw.getInstance().getConfig()
                .getInt("number-format.max-decimals", 2);
    }

    // ── Pure logic (no Bukkit, easy to unit test) ────────────────────

    /**
     * @return the amount, or empty if the input is invalid, zero, negative,
     *         fractional (e.g. 1.0005k) or larger than Integer.MAX_VALUE.
     */
    public static OptionalInt parse(String input, boolean shorthandEnabled) {

        if (input == null) return OptionalInt.empty();

        String s = input.trim();
        if (s.isEmpty() || s.length() > 20) return OptionalInt.empty();

        if (PLAIN.matcher(s).matches()) {
            long value = Long.parseLong(s);
            return value > 0 && value <= Integer.MAX_VALUE
                    ? OptionalInt.of((int) value) : OptionalInt.empty();
        }

        if (!shorthandEnabled) return OptionalInt.empty();

        Matcher m = SHORT.matcher(s);
        if (!m.matches()) return OptionalInt.empty();

        BigDecimal value = new BigDecimal(m.group(1))
                .multiply(BigDecimal.valueOf(multiplier(m.group(2).charAt(0))));

        // Must be a whole number. Money is never silently rounded.
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
            return OptionalInt.empty();
        }
        if (value.compareTo(INT_MAX) > 0) return OptionalInt.empty();

        return OptionalInt.of(value.intValueExact());
    }

    public static String format(long amount, boolean shorthandEnabled, int maxDecimals) {

        if (!shorthandEnabled || amount < K) return Long.toString(amount);

        int decimals = Math.max(0, Math.min(3, maxDecimals));

        long unit;
        String suffix;
        if      (amount >= T) { unit = T; suffix = "t"; }
        else if (amount >= B) { unit = B; suffix = "b"; }
        else if (amount >= M) { unit = M; suffix = "m"; }
        else                  { unit = K; suffix = "k"; }

        // RoundingMode.DOWN: a display can never claim more than the real
        // value (and 999,999 can never turn into "1000k").
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(unit), decimals, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString() + suffix;
    }

    private static long multiplier(char suffix) {
        return switch (Character.toLowerCase(suffix)) {
            case 'k' -> K;
            case 'm' -> M;
            case 'b' -> B;
            default  -> T;
        };
    }
}
