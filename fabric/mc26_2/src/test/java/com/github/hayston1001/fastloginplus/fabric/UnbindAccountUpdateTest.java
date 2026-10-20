/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnbindAccountUpdateTest {
    private static final UUID PREMIUM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @ParameterizedTest
    @ValueSource(strings = {"absent", "same", "distinct"})
    void preparingDoesNotMutateAndSuccessfulSqliteWritePreservesPassword(String cacheKind) throws Exception {
        Entry entry = new Entry();
        Entry cached = cache(cacheKind, entry);
        try (Connection connection = database()) {
            Runnable unbind = EasyAuthAccountUpdate.prepareUnbind(Entry.class, entry, cached, OFFLINE,
                    candidate -> save(connection, entry, false));
            assertBound(entry);
            if (cached != null) {
                assertBound(cached);
            }
            unbind.run();
            assertPassword(entry);
            if (cached != null) {
                assertPassword(cached);
            }
            assertStored(connection, "FALSE", OFFLINE);
        }
    }

    @ParameterizedTest
    @CsvSource({"absent,false", "same,false", "distinct,false",
            "absent,true", "same,true", "distinct,true"})
    void sqliteAbortRestoresFieldsAndAllowsRetryWithoutRestoringDatabase(String cacheKind, boolean throwing)
            throws Exception {
        Entry entry = new Entry();
        Entry cached = cache(cacheKind, entry);
        try (Connection connection = database()) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TRIGGER fail_unbind BEFORE UPDATE ON accounts "
                        + "BEGIN SELECT RAISE(ABORT, 'injected failure'); END");
            }
            Runnable unbind = EasyAuthAccountUpdate.prepareUnbind(Entry.class, entry, cached, OFFLINE,
                    candidate -> save(connection, entry, throwing));
            var failure = assertThrows(FabricMessages.PlayerFailure.class, unbind::run);
            assertEquals("cracked-save-failed", failure.getMessage());
            assertBound(entry);
            if (cached != null) {
                assertBound(cached);
            }
            assertStored(connection, "TRUE", PREMIUM);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DROP TRIGGER fail_unbind");
            }
            unbind.run();
            assertPassword(entry);
            assertStored(connection, "FALSE", OFFLINE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "same", "distinct"})
    void prepareOpenPreservesRowAndEmptyPasswordAndMirrorsCache(String cacheKind) throws Exception {
        Entry entry = new Entry();
        Entry cached = cache(cacheKind, entry);
        entry.password = "";
        if (cached != null) {
            cached.password = "";
        }
        try (Connection connection = database("")) {
            Runnable release = EasyAuthAccountUpdate.prepareOpen(Entry.class, entry, cached,
                    candidate -> save(connection, entry, false));
            assertEquals(Kind.TRUE, entry.onlineAccount);
            assertEquals(PREMIUM, entry.uuid);
            if (cached != null) {
                assertEquals(Kind.TRUE, cached.onlineAccount);
                assertEquals(PREMIUM, cached.uuid);
            }
            release.run();
            assertOpen(entry);
            if (cached != null) {
                assertOpen(cached);
            }
            assertStored(connection, "UNKNOWN", null, "");
        }
    }

    @ParameterizedTest
    @CsvSource({"absent,false", "same,false", "distinct,false",
            "absent,true", "same,true", "distinct,true"})
    void prepareOpenRollsBackAfterSqliteFailureAndCanRetry(String cacheKind, boolean throwing) throws Exception {
        Entry entry = new Entry();
        Entry cached = cache(cacheKind, entry);
        entry.password = "";
        if (cached != null) {
            cached.password = "";
        }
        try (Connection connection = database("")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TRIGGER fail_release BEFORE UPDATE ON accounts "
                        + "BEGIN SELECT RAISE(ABORT, 'injected failure'); END");
            }
            Runnable release = EasyAuthAccountUpdate.prepareOpen(Entry.class, entry, cached,
                    candidate -> save(connection, entry, throwing));
            var failure = assertThrows(FabricMessages.PlayerFailure.class, release::run);
            assertEquals("cracked-release-failed", failure.getMessage());
            assertEquals(Kind.TRUE, entry.onlineAccount);
            assertEquals(PREMIUM, entry.uuid);
            assertEquals("", entry.password);
            if (cached != null) {
                assertEquals(Kind.TRUE, cached.onlineAccount);
                assertEquals(PREMIUM, cached.uuid);
                assertEquals("", cached.password);
            }
            assertStored(connection, "TRUE", PREMIUM, "");
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DROP TRIGGER fail_release");
            }
            release.run();
            assertOpen(entry);
            if (cached != null) {
                assertOpen(cached);
            }
            assertStored(connection, "UNKNOWN", null, "");
        }
    }

    private static Entry cache(String kind, Entry entry) {
        return "absent".equals(kind) ? null : "same".equals(kind) ? entry : new Entry();
    }

    private static Connection database() throws SQLException {
        return database("old-hash");
    }

    private static Connection database(String password) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE accounts (password TEXT, kind TEXT, uuid TEXT)");
            statement.executeUpdate("INSERT INTO accounts VALUES ('" + password + "', 'TRUE', '" + PREMIUM + "')");
        }
        return connection;
    }

    private static boolean save(Connection connection, Entry entry, boolean throwing) {
        try (var statement = connection.prepareStatement("UPDATE accounts SET password=?, kind=?, uuid=?")) {
            statement.setString(1, entry.password);
            statement.setString(2, entry.onlineAccount.name());
            statement.setString(3, entry.uuid == null ? null : entry.uuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            if (throwing) {
                throw new IllegalStateException("SQLite update failed", failure);
            }
            return false;
        }
    }

    private static void assertStored(Connection connection, String kind, UUID uuid) throws SQLException {
        assertStored(connection, kind, uuid, "old-hash");
    }

    private static void assertStored(Connection connection, String kind, UUID uuid, String password) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT * FROM accounts")) {
            result.next();
            assertEquals(password, result.getString("password"));
            assertEquals(kind, result.getString("kind"));
            assertEquals(uuid == null ? null : uuid.toString(), result.getString("uuid"));
            assertEquals(false, result.next());
        }
    }

    private static void assertOpen(Entry entry) {
        assertEquals(Kind.UNKNOWN, entry.onlineAccount);
        assertEquals(null, entry.uuid);
        assertEquals("", entry.password);
        assertEquals("untouched", entry.lastIp);
    }

    private static void assertBound(Entry entry) {
        assertEquals(Kind.TRUE, entry.onlineAccount);
        assertEquals(PREMIUM, entry.uuid);
        assertEquals("old-hash", entry.password);
        assertEquals("untouched", entry.lastIp);
    }

    private static void assertPassword(Entry entry) {
        assertEquals(Kind.FALSE, entry.onlineAccount);
        assertEquals(OFFLINE, entry.uuid);
        assertEquals("old-hash", entry.password);
        assertEquals("untouched", entry.lastIp);
    }

    public enum Kind {
        TRUE, FALSE, UNKNOWN
    }

    public static final class Entry {
        public String password = "old-hash";
        public Kind onlineAccount = Kind.TRUE;
        public UUID uuid = PREMIUM;
        public String lastIp = "untouched";
    }
}
