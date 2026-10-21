package com.bcp.workshop.auth;

import java.nio.charset.StandardCharsets;

public final class AuthTokenSecretValidator {
    public static final String LAB_SECRET = "workshop-token-lab-secret-only-2026-abcdefghijklmnopqrstuvwxyz";

    private AuthTokenSecretValidator() {}

    public static String validate(String secret, String profile) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must be configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must contain at least 32 UTF-8 bytes");
        }
        if ("prod".equals(profile) && LAB_SECRET.equals(secret)) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must not use the laboratory value in production");
        }
        return secret;
    }
}