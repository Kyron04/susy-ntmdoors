package com.ntmdoors.util;

/** Shared validation for the numeric keypad codes used by safes and scanners. */
public final class Codes {

    /** Keeps only decimal digits, stopping at {@code maxLength} characters. */
    public static String sanitise(String raw, int maxLength) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(Math.min(raw.length(), maxLength));
        for (int i = 0; i < raw.length() && out.length() < maxLength; i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }

    private Codes() {
    }
}
