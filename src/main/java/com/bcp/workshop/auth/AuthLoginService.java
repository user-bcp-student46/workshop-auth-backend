package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

@ApplicationScoped
public class AuthLoginService {
    private static final String DEFAULT_CLIENT = "default";
    private static final Pattern VALID_CLIENT = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final LegacyDatabase database;
    private final AuthTokenService tokens;

    public AuthLoginService(LegacyDatabase database, AuthTokenService tokens) {
        this.database = database;
        this.tokens = tokens;
    }

    public LoginResult authenticate(LoginRequest request) {
        String client = request.client == null ? DEFAULT_CLIENT : request.client;
        if (!VALID_CLIENT.matcher(client).matches()) {
            return new InvalidClient();
        }

        UserRecord user = database.findByCredentials(request.username, request.password);
        if (user == null) {
            return new InvalidCredentials();
        }

        return new LoginSuccess(user, tokens.issue(user.username(), user.role(), client));
    }

    public sealed interface LoginResult permits LoginSuccess, InvalidClient, InvalidCredentials {}

    public record LoginSuccess(UserRecord user, String token) implements LoginResult {}

    public record InvalidClient() implements LoginResult {}

    public record InvalidCredentials() implements LoginResult {}
}