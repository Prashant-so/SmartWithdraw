package com.smartwithdraw.update;

public final class Version {

    private Version() {
    }

    /** "v2.1.0" -> "2.1.0" */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() > 1 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')
                && Character.isDigit(s.charAt(1))) {
            s = s.substring(1);
        }
        return s;
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    public static int compare(String a, String b) {
        int[] x = numbers(a);
        int[] y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        // Same numbers: a normal release beats a pre-release (2.1.0 > 2.1.0-beta)
        boolean preA = isPre(a);
        boolean preB = isPre(b);
        return preA == preB ? 0 : (preA ? -1 : 1);
    }

    private static String core(String v) {
        String s = clean(v);
        int cut = s.length();
        int dash = s.indexOf('-');
        int plus = s.indexOf('+');
        if (dash >= 0) cut = Math.min(cut, dash);
        if (plus >= 0) cut = Math.min(cut, plus);
        return s.substring(0, cut);
    }

    private static int[] numbers(String v) {
        String[] parts = core(v).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("\\D.*$", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static boolean isPre(String v) {
        return clean(v).indexOf('-') >= 0;
    }
}
