package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class LegacyDatabase {
    private static final Logger LOG = Logger.getLogger(LegacyDatabase.class.getName());
    private static final String DEFAULT_DB_URL = "jdbc:sqlite:./data/workshop.db";
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-password-for-timing");

    private final String dbUrl;

    public LegacyDatabase() {
        this(System.getenv().getOrDefault("AUTH_DB_URL", DEFAULT_DB_URL));
    }

    LegacyDatabase(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(dbUrl)) {
                connection.setAutoCommit(false);
                try {
                    migrate(connection);
                    connection.commit();
                } catch (SQLException error) {
                    connection.rollback();
                    throw error;
                }
            }
        } catch (Exception error) {
            LOG.log(Level.SEVERE, "Legacy user migration failed. Users with unhashed passwords cannot log in until it succeeds.", error);
        }
    }

    private void migrate(Connection connection) throws SQLException {
        try (PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)")) {
            create.executeUpdate();
        }
        seed(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
        seed(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");

        Map<Long, String> unhashed = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT id, password FROM legacy_users");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String stored = rows.getString("password");
                if (!PasswordHasher.isHashed(stored)) {
                    unhashed.put(rows.getLong("id"), stored);
                }
            }
        }
        try (PreparedStatement update = connection.prepareStatement("UPDATE legacy_users SET password=? WHERE id=?")) {
            for (Map.Entry<Long, String> row : unhashed.entrySet()) {
                update.setString(1, PasswordHasher.hash(row.getValue()));
                update.setLong(2, row.getKey());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void seed(Connection connection, String username, String password, String displayName, String role) throws SQLException {
        try (PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM legacy_users WHERE username=?")) {
            exists.setString(1, username);
            try (ResultSet result = exists.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            insert.setString(1, username);
            insert.setString(2, PasswordHasher.hash(password));
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        String sql = "SELECT id, username, display_name, role, password FROM legacy_users WHERE username=?";
        try (Connection connection = DriverManager.getConnection(dbUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    // Hash anyway so unknown users take the same time as known ones.
                    PasswordHasher.matches(password, DUMMY_HASH);
                    return null;
                }
                if (!PasswordHasher.matches(password, result.getString("password"))) {
                    return null;
                }
                return new UserRecord(result.getLong("id"), result.getString("username"), result.getString("display_name"), result.getString("role"));
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }
}
