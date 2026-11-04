package com.ntmdoors.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Password hashing shared by the monitor screen (client) and the monitor desk tile entity (server).
 * Only the SHA-256 hash is stored; the plain password never touches NBT.
 */
public final class MonitorPasswords {

    /** Maximum password length accepted by the monitor screen. */
    public static final int MAX_LENGTH = 24;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Returns the lowercase SHA-256 hash of a password, or an empty string for null. */
    public static String hash(String password) {
        if (password == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xFF;
                out[i * 2] = HEX[value >>> 4];
                out[i * 2 + 1] = HEX[value & 0xF];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** True for a 64-character lowercase hexadecimal SHA-256 string. */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.length() != 64) return false;
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    /** Constant-time comparison of the stored hash and a candidate hash. */
    public static boolean matches(String stored, String candidate) {
        if (!isValidHash(stored) || !isValidHash(candidate)) return false;
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(StandardCharsets.US_ASCII));
    }

    private MonitorPasswords() {
    }
}
