package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AuthLoginCharacterizationTest {
    private static final String LOGIN_PATH = "/auth/login";

    @Test
    void logsInWithTrainerUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"trainer\",\"password\":\"Trainer123!\",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("user.displayName", is("Workshop Trainer"))
                .body("user.role", is("TRAINER"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void rejectsNullRequestBody() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingOrBlankUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingOrBlankPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"   \"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsWrongPasswordWithStableMessage() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrecta\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsUnknownUserWithSameMessageFormat() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"nadie\",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user nadie or password"));
    }

    @Test
    void tokenKeepsLegacyFourPartFormat() {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .extract().path("token");

        String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");

        assertEquals(4, parts.length);
        assertEquals("student", parts[0]);
        assertEquals("STUDENT", parts[1]);
    }
}
