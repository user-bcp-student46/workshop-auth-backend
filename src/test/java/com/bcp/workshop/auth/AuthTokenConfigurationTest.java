package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthTokenConfigurationTest {
    @Inject
    AuthTokenConfiguration configuration;

    @Test
    void resolvesEnvironmentOverrideOrTestProfileFallback() {
        String expected = System.getenv("AUTH_TOKEN_SECRET");
        if (expected == null) {
            expected = AuthTokenSecretValidator.LAB_SECRET;
        }

        assertTrue(expected.equals(configuration.secret()),
                "AUTH_TOKEN_SECRET should override the test profile fallback without changing its value");
    }
}