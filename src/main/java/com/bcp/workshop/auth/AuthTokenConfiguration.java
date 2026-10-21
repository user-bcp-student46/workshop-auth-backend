package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Startup
@ApplicationScoped
public class AuthTokenConfiguration {
    private final String secret;

    @Inject
    public AuthTokenConfiguration(
            @ConfigProperty(name = "auth.token.secret") String secret,
            @ConfigProperty(name = "quarkus.profile", defaultValue = "dev") String profile) {
        this.secret = AuthTokenSecretValidator.validate(secret, profile);
    }

    public String secret() {
        return secret;
    }
}