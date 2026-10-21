package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@QuarkusTest
class AuthResourceCharacterizationTest {
    private static final String TEST_DB_URL = "jdbc:sqlite:./target/workshop-auth-characterization.db";
    private static final String FIXTURE_USERNAME = "characterization-html-user";
    private static final String FIXTURE_PASSWORD = "CharacterizationOnly123!";

    @BeforeAll
    static void verifiesIsolatedDatabaseConfiguration() {
        assertEquals(TEST_DB_URL, System.getenv("AUTH_DB_URL"), "AUTH_DB_URL must point to the test database before the JVM starts");
        assertTrue(Files.exists(Path.of("target/workshop-auth-characterization.db")), "the isolated test database should have been initialized");
    }

    @ParameterizedTest
    @MethodSource("invalidLoginRequests")
    void preservesRequiredFieldValidationAndPriority(String body, String message) {
        assertBadRequest(body, message);
    }

    private static Stream<Arguments> invalidLoginRequests() {
        return Stream.of(
                arguments("null", "Request is null"),
                arguments("{\"password\":\"Workshop123!\"}", "Username is required"),
                arguments("{\"username\":null,\"password\":\"Workshop123!\"}", "Username is required"),
                arguments("{\"username\":\"\",\"password\":\"Workshop123!\"}", "Username is required"),
                arguments("{\"username\":\"   \",\"password\":\"Workshop123!\"}", "Username is required"),
                arguments("{\"username\":\"student\"}", "Password is required"),
                arguments("{\"username\":\"student\",\"password\":null}", "Password is required"),
                arguments("{\"username\":\"student\",\"password\":\"\"}", "Password is required"),
                arguments("{\"username\":\"student\",\"password\":\"   \"}", "Password is required"),
                arguments("{\"username\":\" \",\"password\":\"\"}", "Username is required")
        );
    }

    @Test
    void preservesUnauthorizedResponseForIncorrectAndUnknownCredentials() {
        assertUnauthorized("{\"username\":\"student\",\"password\":\"WrongPassword!\"}",
                "Invalid user student or password");
        assertUnauthorized("{\"username\":\"missing-characterization-user\",\"password\":\"AnyPassword!\"}",
                "Invalid user missing-characterization-user or password");
    }

    @Test
    void acceptsAbsentNullAndValidClientAndUnknownProperties() {
        assertSuccessfulLogin("{\"username\":\"student\",\"password\":\"Workshop123!\"}");
        assertSuccessfulLogin("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}");
        assertSuccessfulLogin("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}");
        assertSuccessfulLogin("{\"username\":\"student\",\"password\":\"Workshop123!\",\"unexpected\":\"ignored\"}");
    }

    @Test
    void escapesDisplayNameWithoutMutatingPersistedValue() throws Exception {
        String displayName = "<b>Lab</b> & \"Test\"";
        insertFixture(displayName);

        try {
            Response response = postLogin("{\"username\":\"" + FIXTURE_USERNAME + "\",\"password\":\"" + FIXTURE_PASSWORD + "\"}");
            assertEquals(200, response.statusCode());
            assertEquals("&lt;b&gt;Lab&lt;/b&gt; &amp; &quot;Test&quot;", response.jsonPath().getString("user.displayName"));
            assertEquals(displayName, readFixtureDisplayName());
        } finally {
            deleteFixture();
        }
    }

    @Test
    void returnsGenericInternalErrorBodyForNullAndNonNullMessages() {
        assertInternalError(new IllegalStateException("synthetic characterization diagnostic"),
                "Unknown error");
        assertInternalError(new IllegalStateException(), "Unknown error");
    }

    @Test
    void keepsHealthPublicAndReportsHealthyStatus() {
        Response response = given().when().get("/q/health").then().extract().response();

        assertEquals(Status.OK.getStatusCode(), response.statusCode());
        assertEquals("UP", response.jsonPath().getString("status"));
        assertInstanceOf(List.class, response.jsonPath().get("checks"));
    }

    private static Response postLogin(String body) {
        return given()
                .contentType("application/json")
                .body(body)
                .when().post("/auth/login")
                .then()
                .extract().response();
    }

    private static void assertBadRequest(String body, String message) {
        Response response = postLogin(body);
        assertEquals(400, response.statusCode());
        assertEquals(Map.of("message", message), response.jsonPath().getMap(""));
    }

    private static void assertUnauthorized(String body, String message) {
        Response response = postLogin(body);
        assertEquals(401, response.statusCode());
        Map<?, ?> responseBody = response.jsonPath().getMap("");
        assertEquals(Map.of("message", message), responseBody);
        assertFalse(responseBody.containsKey("token"));
    }

    private static void assertSuccessfulLogin(String body) {
        Response response = postLogin(body);
        assertEquals(200, response.statusCode());
        Map<?, ?> responseBody = response.jsonPath().getMap("");
        assertEquals(Set.of("token", "expiresIn", "user", "debug"), responseBody.keySet());
        assertInstanceOf(String.class, responseBody.get("token"));
        assertEquals(3600, responseBody.get("expiresIn"));
        assertEquals("student", response.jsonPath().getString("user.username"));
    }

    private static void assertInternalError(RuntimeException failure, String expectedMessage) {
        AuthResource resource = new AuthResource();
        resource.loginService = new ThrowingLoginService(failure);
        LoginRequest request = new LoginRequest();
        request.username = "characterization-user";
        request.password = "CharacterizationOnly123!";

        jakarta.ws.rs.core.Response response = resource.login(request);
        assertEquals(500, response.getStatus());
        assertEquals(Map.of(
                "message", expectedMessage,
                "exception", failure.getClass().getName()
        ), response.getEntity());
    }

    private static void insertFixture(String displayName) throws Exception {
        try (Connection connection = DriverManager.getConnection(TEST_DB_URL);
             PreparedStatement delete = connection.prepareStatement("DELETE FROM legacy_users WHERE username = ?");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            delete.setString(1, FIXTURE_USERNAME);
            delete.executeUpdate();
            insert.setString(1, FIXTURE_USERNAME);
            insert.setString(2, FIXTURE_PASSWORD);
            insert.setString(3, displayName);
            insert.setString(4, "STUDENT");
            insert.executeUpdate();
        }
    }

    private static String readFixtureDisplayName() throws Exception {
        try (Connection connection = DriverManager.getConnection(TEST_DB_URL);
             PreparedStatement query = connection.prepareStatement("SELECT display_name FROM legacy_users WHERE username = ?")) {
            query.setString(1, FIXTURE_USERNAME);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                return result.getString("display_name");
            }
        }
    }

    private static void deleteFixture() throws Exception {
        try (Connection connection = DriverManager.getConnection(TEST_DB_URL);
             PreparedStatement delete = connection.prepareStatement("DELETE FROM legacy_users WHERE username = ?")) {
            delete.setString(1, FIXTURE_USERNAME);
            delete.executeUpdate();
        }
    }

    private static final class ThrowingLoginService extends AuthLoginService {
        private final RuntimeException failure;

        private ThrowingLoginService(RuntimeException failure) {
            super(null, null);
            this.failure = failure;
        }

        @Override
        public LoginResult authenticate(LoginRequest request) {
            throw failure;
        }
    }
}