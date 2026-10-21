package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthLoginServiceTest {
    private static final String SECRET = "synthetic-login-service-test-secret-value-0001";

    @Test
    void resolvesAbsentAndNullClientToDefault() {
        RecordingDatabase database = new RecordingDatabase();
        AuthLoginService service = service(database);

        LoginRequest absent = request(null);
        AuthLoginService.LoginSuccess absentResult = assertInstanceOf(AuthLoginService.LoginSuccess.class,
                service.authenticate(absent));
        assertEquals("default", verifiedClient(absentResult.token()));

        LoginRequest nullClient = request(null);
        AuthLoginService.LoginSuccess nullResult = assertInstanceOf(AuthLoginService.LoginSuccess.class,
                service.authenticate(nullClient));
        assertEquals("default", verifiedClient(nullResult.token()));
        assertEquals(2, database.calls);
    }

    @Test
    void rejectsInvalidClientBeforeCredentialLookup() {
        RecordingDatabase database = new RecordingDatabase();
        LoginRequest request = request("angular workshop!");

        assertInstanceOf(AuthLoginService.InvalidClient.class, service(database).authenticate(request));
        assertEquals(0, database.calls);
    }

    @ParameterizedTest
    @MethodSource("validClientValues")
    void acceptsClientValuesWithinTheApprovedPattern(String client) {
        RecordingDatabase database = new RecordingDatabase();
        LoginRequest request = request(client);

        AuthLoginService.LoginSuccess result = assertInstanceOf(AuthLoginService.LoginSuccess.class,
                service(database).authenticate(request));
        assertEquals(client, verifiedClient(result.token()));
        assertEquals(1, database.calls);
    }

    private static Stream<String> validClientValues() {
        return Stream.of("a", "a" + "b".repeat(63), "client._-0123456789");
    }

    @ParameterizedTest
    @MethodSource("invalidClientValues")
    void rejectsEmptyOverlongOrInvalidClientWithoutLookingUpCredentials(String client) {
        RecordingDatabase database = new RecordingDatabase();
        assertInstanceOf(AuthLoginService.InvalidClient.class,
                service(database).authenticate(request(client)));
        assertEquals(0, database.calls);
    }

    private static Stream<String> invalidClientValues() {
        return Stream.of("", " ", "a" + "b".repeat(64), "angular workshop!", "client/one");
    }

    @Test
    void doesNotNormalizeClientValues() {
        RecordingDatabase database = new RecordingDatabase();
        AuthLoginService.LoginSuccess result = assertInstanceOf(AuthLoginService.LoginSuccess.class,
                service(database).authenticate(request("Client.Name-1_")));

        assertEquals("Client.Name-1_", verifiedClient(result.token()));
    }

    private static AuthLoginService service(RecordingDatabase database) {
        AuthTokenService tokens = new AuthTokenService(SECRET,
                Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));
        return new AuthLoginService(database, tokens);
    }

    private static String verifiedClient(String token) {
        AuthTokenService.VerifiedToken verified = assertInstanceOf(AuthTokenService.VerifiedToken.class,
                new AuthTokenService(SECRET,
                        Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)).verify(token));
        return verified.claims().client();
    }

    private static LoginRequest request(String client) {
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        request.client = client;
        return request;
    }

    private static final class RecordingDatabase extends LegacyDatabase {
        private int calls;

        @Override
        public UserRecord findByCredentials(String username, String password) {
            calls++;
            return new UserRecord(1, username, "Workshop Student", "STUDENT");
        }
    }
}