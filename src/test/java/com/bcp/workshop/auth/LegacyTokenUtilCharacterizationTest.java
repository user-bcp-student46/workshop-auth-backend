package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTokenUtilCharacterizationTest {
    @Test
    void preservesLegacyBase64TokenStructureAndApproximateExpiry() {
        long beforeCreation = Instant.now().getEpochSecond();
        String token = LegacyTokenUtil.create("student", "STUDENT");
        long afterCreation = Instant.now().getEpochSecond();

        String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|", -1);

        assertEquals(4, parts.length, "the legacy payload has four pipe-delimited parts");
        assertEquals("student", parts[0]);
        assertEquals("STUDENT", parts[1]);
        assertTrue(parts[3].equals("WORKSHOP_SECRET_2026"), "the legacy payload ends with its laboratory secret");

        long expiry = Long.parseLong(parts[2]);
        assertTrue(expiry >= beforeCreation + 3599, "expiry is approximately one hour after token creation");
        assertTrue(expiry <= afterCreation + 3601, "expiry is approximately one hour after token creation");
    }
}