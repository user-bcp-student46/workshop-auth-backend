package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        assertSuccessfulLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
    }

    @Test
    void logsInWithWorkshopTrainer() {
        assertSuccessfulLogin("trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    private void assertSuccessfulLogin(String username, String password, String displayName, String role) {
        Response response = given()
                .contentType("application/json")
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .extract().response();

        assertEquals(200, response.statusCode());
        var body = response.jsonPath().getMap("");
        assertEquals(java.util.Set.of("token", "expiresIn", "user", "debug"), body.keySet());
        assertInstanceOf(String.class, body.get("token"));
        assertEquals(3600, body.get("expiresIn"));
        assertEquals("authenticated-with-legacy-flow", body.get("debug"));

        var user = (java.util.Map<?, ?>) body.get("user");
        assertEquals(java.util.Set.of("username", "displayName", "role"), user.keySet());
        assertEquals(username, user.get("username"));
        assertEquals(displayName, user.get("displayName"));
        assertEquals(role, user.get("role"));
        assertNotNull(body.get("token"));
        assertTrue(!((String) body.get("token")).isBlank());
    }
}
