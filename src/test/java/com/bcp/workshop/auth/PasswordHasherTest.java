package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashUsesVersionedFormatAndDoesNotContainPlaintext() {
        String stored = PasswordHasher.hash("Secret1!");

        assertTrue(stored.startsWith("pbkdf2_sha256$" + PasswordHasher.ITERATIONS + "$"));
        assertFalse(stored.contains("Secret1!"));
    }

    @Test
    void matchesAcceptsOnlyTheOriginalPassword() {
        String stored = PasswordHasher.hash("Secret1!");

        assertTrue(PasswordHasher.matches("Secret1!", stored));
        assertFalse(PasswordHasher.matches("Secret1?", stored));
        assertFalse(PasswordHasher.matches("", stored));
    }

    @Test
    void matchesRejectsPlaintextAndMalformedStoredValues() {
        assertFalse(PasswordHasher.matches("Workshop123!", "Workshop123!"));
        assertFalse(PasswordHasher.matches("x", "pbkdf2_sha256$abc$AAAA$AAAA"));
        assertFalse(PasswordHasher.matches("x", "pbkdf2_sha256$0$AAAA$AAAA"));
        assertFalse(PasswordHasher.matches("x", "pbkdf2_sha256$600000$AAAA"));
        assertFalse(PasswordHasher.matches("x", null));
    }
}
