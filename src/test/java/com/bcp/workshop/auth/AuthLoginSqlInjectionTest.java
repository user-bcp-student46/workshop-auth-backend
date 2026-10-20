package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AuthLoginSqlInjectionTest {
    private static final String LOGIN_PATH = "/auth/login";

    @Test
    void quoteInUsernameDoesNotBypassPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student' --\",\"password\":\"incorrecta\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("token", nullValue())
                .body("user", nullValue());
    }

    @Test
    void quoteInPasswordDoesNotBypassPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"' OR '1'='1\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("token", nullValue())
                .body("message", is("Invalid user student or password"));
    }
}
