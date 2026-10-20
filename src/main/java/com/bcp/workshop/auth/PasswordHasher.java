package com.bcp.workshop.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordHasher {
    static final String PREFIX = "pbkdf2_sha256";
    static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    // Format: pbkdf2_sha256$<iterations>$<base64 salt>$<base64 key>. Only values in this format are ever verified.
    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS, KEY_BITS);
        return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(key);
    }

    static boolean isHashed(String stored) {
        return parse(stored) != null;
    }

    static boolean matches(String password, String stored) {
        String[] parts = parse(stored);
        if (parts == null) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = derive(password, salt, iterations, expected.length * 8);
        return MessageDigest.isEqual(expected, actual);
    }

    // Returns the four fields of a well-formed stored value, or null when the value is not a complete PBKDF2 hash.
    private static String[] parse(String stored) {
        if (stored == null) {
            return null;
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return null;
        }
        try {
            if (Integer.parseInt(parts[1]) <= 0
                    || Base64.getDecoder().decode(parts[2]).length == 0
                    || Base64.getDecoder().decode(parts[3]).length == 0) {
                return null;
            }
        } catch (IllegalArgumentException error) {
            return null;
        }
        return parts;
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", error);
        } finally {
            spec.clearPassword();
        }
    }
}
