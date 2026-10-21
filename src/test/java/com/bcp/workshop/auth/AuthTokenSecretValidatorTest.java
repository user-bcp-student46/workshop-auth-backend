package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AuthTokenSecretValidatorTest {
    private static final String VALID_SYNTHETIC_SECRET = "synthetic-test-secret-value-with-at-least-32-bytes";

    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    void rejectsMissingWeakOrProductionLaboratorySecrets(String secret, String profile) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> AuthTokenSecretValidator.validate(secret, profile));
        assertFalse(failure.getMessage().contains(AuthTokenSecretValidator.LAB_SECRET));
        if (secret != null && !secret.isBlank()) {
            assertFalse(failure.getMessage().contains(secret));
        }
    }

    private static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
                arguments(null, "dev"),
                arguments("", "test"),
                arguments("   ", "dev"),
                arguments("short", "test"),
                arguments(AuthTokenSecretValidator.LAB_SECRET, "prod")
        );
    }

    @Test
    void acceptsConfiguredSyntheticSecretWithoutChangingItsBytes() {
        assertEquals(VALID_SYNTHETIC_SECRET,
                AuthTokenSecretValidator.validate(VALID_SYNTHETIC_SECRET, "prod"));
    }

    @Test
    void acceptsLaboratorySecretOnlyInNonProductionProfiles() {
        assertEquals(AuthTokenSecretValidator.LAB_SECRET,
                AuthTokenSecretValidator.validate(AuthTokenSecretValidator.LAB_SECRET, "test"));
        assertEquals(AuthTokenSecretValidator.LAB_SECRET,
                AuthTokenSecretValidator.validate(AuthTokenSecretValidator.LAB_SECRET, "dev"));
    }
}