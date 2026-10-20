package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseMigrationTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void registerSqliteDriver() throws Exception {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    void freshDatabaseStoresOnlyHashedSeedPasswords() throws Exception {
        String url = urlFor("fresh.db");
        new LegacyDatabase(url).initialize();

        List<String> stored = storedPasswords(url);
        assertEquals(2, stored.size());
        for (String password : stored) {
            assertTrue(PasswordHasher.isHashed(password));
        }
        assertFalse(stored.contains("Workshop123!"));
        assertFalse(stored.contains("Trainer123!"));
    }

    @Test
    void seededUsersCanLogInWithOriginalPasswords() {
        LegacyDatabase database = new LegacyDatabase(urlFor("login.db"));
        database.initialize();

        UserRecord student = database.findByCredentials("student", "Workshop123!");
        assertNotNull(student);
        assertEquals("STUDENT", student.role());
        assertNotNull(database.findByCredentials("trainer", "Trainer123!"));
        assertNull(database.findByCredentials("student", "Trainer123!"));
        assertNull(database.findByCredentials("nobody", "Workshop123!"));
    }

    @Test
    void migratesLegacyPlaintextRowsInPlace() throws Exception {
        String url = urlFor("legacy.db");
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO legacy_users(username,password,display_name,role) VALUES ('ana','ana-secret','Ana','STUDENT')");
        }

        LegacyDatabase database = new LegacyDatabase(url);
        database.initialize();

        for (String password : storedPasswords(url)) {
            assertTrue(PasswordHasher.isHashed(password));
            assertFalse(password.equals("ana-secret"));
        }
        UserRecord ana = database.findByCredentials("ana", "ana-secret");
        assertNotNull(ana);
        assertEquals("ana", ana.username());
        assertNull(database.findByCredentials("ana", "ana-secret-typo"));
    }

    @Test
    void initializeIsIdempotent() throws Exception {
        String url = urlFor("idempotent.db");
        LegacyDatabase database = new LegacyDatabase(url);
        database.initialize();
        Map<String, String> first = storedByUser(url);

        database.initialize();
        Map<String, String> second = storedByUser(url);

        assertEquals(2, second.size());
        assertEquals(first, second);
    }

    private String urlFor(String name) {
        return "jdbc:sqlite:" + dir.resolve(name).toAbsolutePath();
    }

    private List<String> storedPasswords(String url) throws Exception {
        return new ArrayList<>(storedByUser(url).values());
    }

    private Map<String, String> storedByUser(String url) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement("SELECT username, password FROM legacy_users");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.put(rows.getString("username"), rows.getString("password"));
            }
        }
        return result;
    }
}
