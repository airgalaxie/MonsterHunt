package de.airgalaxie.monsterhuntreloaded;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDriverAvailabilityTest {
    @Test
    void configuredFallbackDriverSupportsRuntimeDatabaseAndBackup(@TempDir Path directory) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path database = directory.resolve("primary.db");
        Path backup = directory.resolve("backup.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE probe(value TEXT NOT NULL)");
                statement.executeUpdate("INSERT INTO probe VALUES('available')");
            }
            try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, backup.toString());
                statement.execute();
            }
        }
        assertTrue(backup.toFile().isFile());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + backup);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT value FROM probe")) {
            assertTrue(result.next());
            assertEquals("available", result.getString(1));
        }
    }
}
