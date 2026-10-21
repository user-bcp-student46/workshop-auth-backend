package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthLoginIntegrationTest {
    @Inject
    AuthTokenService tokens;

    @Test
    void emitsJwtWithPersistedIdentityAndPreservesSuccessBody() {
        Instant sentAt = Instant.now();
        Response response = postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\","
                + "\"client\":\"angular-workshop\"}");
        Instant receivedAt = Instant.now();

        assertEquals(200, response.statusCode());
        Map<?, ?> body = response.jsonPath().getMap("");
        assertEquals(Set.of("token", "expiresIn", "user", "debug"), body.keySet());
        assertEquals(3600, body.get("expiresIn"));
        assertEquals("authenticated-with-legacy-flow", body.get("debug"));
        assertEquals("Workshop Student", response.jsonPath().getString("user.displayName"));
        assertEquals("STUDENT", response.jsonPath().getString("user.role"));

        AuthTokenService.VerifiedToken token = assertInstanceOf(AuthTokenService.VerifiedToken.class,
                tokens.verify(response.jsonPath().getString("token")));
        assertEquals("student", token.claims().subject());
        assertEquals("STUDENT", token.claims().role());
        assertEquals("angular-workshop", token.claims().client());
        assertEquals(3600, token.claims().expiresAt() - token.claims().issuedAt());
        long remaining = token.claims().expiresAt() - receivedAt.getEpochSecond();
        long observedDelta = Math.abs(3600 - remaining);
        assertTrue(observedDelta <= 5, "expiresIn must match exp minus response observation within five seconds");
        assertTrue(token.claims().issuedAt() >= sentAt.getEpochSecond(),
                "iat should be captured after request submission");
    }

    @Test
    void usesDefaultClientWhenClientIsAbsentOrNull() {
        assertEquals("default", tokenClient(postLogin(
                "{\"username\":\"student\",\"password\":\"Workshop123!\"}")));
        assertEquals("default", tokenClient(postLogin(
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}")));
    }

    @ParameterizedTest
        @ValueSource(strings = {"", " ", "angular workshop!", "client/one", "a/b_c"})
    void rejectsInvalidClientWithTheApprovedBadRequest(String client) {
        Response response = postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\","
                + "\"client\":\"" + client + "\"}");

        assertEquals(400, response.statusCode());
        assertEquals(Map.of("message", "Client is invalid"), response.jsonPath().getMap(""));
    }

        @ParameterizedTest
        @MethodSource("requiredFieldsBeforeClient")
        void preservesRequiredFieldPriorityWhenClientIsAlsoInvalid(String body, String expectedMessage) {
                Response response = postLogin(body);

                assertEquals(400, response.statusCode());
                assertEquals(Map.of("message", expectedMessage), response.jsonPath().getMap(""));
        }

        private static Stream<Arguments> requiredFieldsBeforeClient() {
                return Stream.of(
                                Arguments.of("{\"username\":\" \",\"password\":\"\",\"client\":\"bad / client\"}",
                                                "Username is required"),
                                Arguments.of("{\"username\":\"student\",\"password\":\"\",\"client\":\"bad / client\"}",
                                                "Password is required")
                );
        }

    private static Response postLogin(String body) {
        return given()
                .contentType("application/json")
                .body(body)
                .when().post("/auth/login")
                .then()
                .extract().response();
    }

    private String tokenClient(Response response) {
        assertEquals(200, response.statusCode());
        AuthTokenService.VerifiedToken verified = assertInstanceOf(AuthTokenService.VerifiedToken.class,
                tokens.verify(response.jsonPath().getString("token")));
        return verified.claims().client();
    }
}