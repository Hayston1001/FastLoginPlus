/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SQLiteAccountReaderTest {
    private static final UUID VERIFIED = UUID.fromString("abcdef12-abcd-4abc-8def-123456789abc");

    @Test
    void findsOneRowByCanonicalUuidWithoutReturningUnrelatedData() throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createTable(connection, "easyauth");
            insert(connection, "easyauth", "OldName", VERIFIED.toString(), "{\"password\":\"\"}");
            insert(connection, "easyauth", "Unrelated", UUID.randomUUID().toString(), "not-json");

            assertEquals(List.of(new SQLiteAccountReader.Row(
                    "OldName", "oldname", VERIFIED.toString(), "{\"password\":\"\"}")),
                    SQLiteAccountReader.findByUuid(connection, "easyauth", VERIFIED));
        }
    }

    @Test
    void returnsEveryMatchingRowForConflictDetection() throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createTable(connection, "easyauth");
            insert(connection, "easyauth", "OldName", VERIFIED.toString(), "{}");
            insert(connection, "easyauth", "AnotherName", VERIFIED.toString(), "{}");

            assertEquals(List.of("AnotherName", "OldName"), SQLiteAccountReader
                    .findByUuid(connection, "easyauth", VERIFIED).stream()
                    .map(SQLiteAccountReader.Row::username).sorted().toList());
        }
    }

    @Test
    void returnsEmptyListWhenTheVerifiedUuidHasNoRows() throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createTable(connection, "easyauth");
            insert(connection, "easyauth", "Unknown", null, "{}");
            insert(connection, "easyauth", "Unrelated", UUID.randomUUID().toString(), "{}");

            assertEquals(List.of(), SQLiteAccountReader.findByUuid(connection, "easyauth", VERIFIED));
        }
    }

    @Test
    void uuidQueryFailureIsNotAnEmptyMatch() throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> SQLiteAccountReader.findByUuid(connection, "missing_table", VERIFIED));
            org.junit.jupiter.api.Assertions.assertInstanceOf(SQLException.class, failure.getCause());
        }
    }

    @Test
    void uuidQueryQuotesTheConfiguredTableName() throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            String table = "custom\"table";
            createTable(connection, table);
            insert(connection, table, "OldName", VERIFIED.toString(), "{}");

            assertEquals("OldName", SQLiteAccountReader.findByUuid(connection, table, VERIFIED).getFirst().username());
        }
    }

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
        assertThrows(IllegalStateException.class,
                () -> SQLiteAccountReader.findByUuid(connection, "easyauth", VERIFIED));
    }

    private static void createTable(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE \"" + table.replace("\"", "\"\"")
                    + "\" (username TEXT, username_lower TEXT, uuid TEXT, data TEXT)");
        }
    }

    private static void insert(Connection connection, String table, String name, String uuid, String data)
            throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO \"" + table.replace("\"", "\"\"")
                + "\" VALUES (?, ?, ?, ?)")) {
            statement.setString(1, name);
            statement.setString(2, name.toLowerCase(java.util.Locale.ROOT));
            statement.setString(3, uuid);
            statement.setString(4, data);
            statement.executeUpdate();
        }
    }
}
