package io.github.idymian.geops.coupons.domain;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Coupon codes: 8 characters from an alphabet without look-alikes (no 0/O, 1/I/L),
 * so a cashier can type one from a phone screen without mistakes. 31^8 ≈ 850 billion combinations.
 */
public final class CouponCodes {

    public static final int LENGTH = 8;

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private CouponCodes() {
    }

    public static String generate() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }

    /** {@code " k7qm-4xpa "} becomes {@code "K7QM4XPA"}: what the cashier types may include dashes or spaces. */
    public static String normalize(String raw) {
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
