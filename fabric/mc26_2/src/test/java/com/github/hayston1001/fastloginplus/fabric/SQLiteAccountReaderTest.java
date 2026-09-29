/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SQLiteAccountReaderTest {

    @Test
    void distinguishesMissingAccountFromFailedQuery() throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE easyauth (username TEXT, username_lower TEXT, "
                    + "uuid TEXT, data TEXT)");
            statement.executeUpdate("INSERT INTO easyauth VALUES ('Alex', 'alex', 'uuid-one', '{}')");

            assertEquals("Alex", SQLiteAccountReader.read(connection, "easyauth", "Alex", false).username());
            assertNull(SQLiteAccountReader.read(connection, "easyauth", "Missing", false));

            statement.executeUpdate("DROP TABLE easyauth");
            assertThrows(IllegalStateException.class,
                    () -> SQLiteAccountReader.read(connection, "easyauth", "Missing", false));
        }
    }

    @Test
    void rejectsClosedConnection() throws SQLException {
        var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        connection.close();
        assertThrows(IllegalStateException.class,
                () -> SQLiteAccountReader.read(connection, "easyauth", "Alex", false));
    }
}
