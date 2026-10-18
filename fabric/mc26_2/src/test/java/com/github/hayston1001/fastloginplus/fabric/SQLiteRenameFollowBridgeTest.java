/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nikitacartes.easyauth.storage.database.SQLite;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class SQLiteRenameFollowBridgeTest {
    private static final UUID VERIFIED = UUID.fromString("abcdef12-abcd-4abc-8def-123456789abc");
    private static final String NAME = "NewName";
    private static final List<String> PARSED = new ArrayList<>();
    private EasyAuth344Bridge bridge;
    private MinecraftServer server;

    @BeforeEach
    void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PARSED.clear();
        Cache.entry = new Entry(NAME, "newname", null, "{\"onlineAccount\":\"UNKNOWN\"}");
        PARSED.clear();
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        set("easyAuth", EasyAuth.class);
        set("entryType", Entry.class);
        set("playersCache", Cache.class);
        set("pendingBindings", new ConcurrentHashMap<String, EasyAuth344Bridge.PendingBinding>());
        server = mock(MinecraftServer.class);
        doAnswer(call -> {
            CompletableFuture<Void> result = new CompletableFuture<>();
            try {
                call.getArgument(0, Runnable.class).run();
                result.complete(null);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
            return result;
        }).when(server).submit(any(Runnable.class));
    }

    @Test
    void sqliteLookupNeverParsesUnrelatedRowsOrCallsGetAllData() throws Exception {
        try (var connection = database()) {
            insert(connection, NAME, null, "{\"onlineAccount\":\"UNKNOWN\"}");
            insert(connection, "Unrelated", UUID.randomUUID().toString(), "invalid-json");
            EasyAuth.DB = new SQLite(connection, "easyauth");

            bridge.beforeConfigure(server, claim());

            assertEquals(List.of(NAME), PARSED);
            assertEquals("UNKNOWN", Cache.entry.onlineAccount);
            assertEquals(2, countRows(connection));
        }
    }

    @Test
    void sqliteCandidatesStillUseThePureOwnershipFilter() throws Exception {
        try (var connection = database()) {
            insert(connection, NAME, null, "{\"onlineAccount\":\"UNKNOWN\"}");
            insert(connection, "Unbound", VERIFIED.toString(), "{\"onlineAccount\":\"FALSE\"}");
            insert(connection, "Unrelated", UUID.randomUUID().toString(), "invalid-json");
            EasyAuth.DB = new SQLite(connection, "easyauth");

            bridge.beforeConfigure(server, claim());

            assertEquals(List.of("Unbound", NAME), PARSED);
            assertEquals("UNKNOWN", Cache.entry.onlineAccount);
            assertEquals(3, countRows(connection));
        }
    }

    @Test
    void sqliteDuplicateMatchesRejectWithoutChangingAnyRow() throws Exception {
        try (var connection = database()) {
            insert(connection, NAME, null, "{\"onlineAccount\":\"UNKNOWN\"}");
            insert(connection, "OldOne", VERIFIED.toString(), "{\"onlineAccount\":\"TRUE\"}");
            insert(connection, "OldTwo", VERIFIED.toString(), "{\"onlineAccount\":\"TRUE\"}");
            insert(connection, "Unrelated", UUID.randomUUID().toString(), "invalid-json");
            EasyAuth.DB = new SQLite(connection, "easyauth");

            Throwable failure = assertThrows(IllegalStateException.class,
                    () -> bridge.beforeConfigure(server, claim()));
            assertInstanceOf(FabricMessages.PlayerFailure.class, rootCause(failure));
            assertEquals("rename-conflict", rootCause(failure).getMessage());
            assertEquals(List.of("OldOne", "OldTwo"), PARSED.stream().sorted().toList());
            assertEquals("UNKNOWN", Cache.entry.onlineAccount);
            assertEquals(4, countRows(connection));
        }
    }

    @Test
    void sqliteQueryFailureRejectsRatherThanFallingBackOrTreatingItAsNoMatch() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            EasyAuth.DB = new SQLite(connection, "missing_table");

            Throwable failure = assertThrows(IllegalStateException.class,
                    () -> bridge.beforeConfigure(server, claim()));

            assertInstanceOf(SQLException.class, rootCause(failure));
            assertEquals(List.of(), PARSED);
            assertEquals("UNKNOWN", Cache.entry.onlineAccount);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        while (failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private static Connection database() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE easyauth (username TEXT PRIMARY KEY, username_lower TEXT, "
                    + "uuid TEXT, data TEXT)");
        }
        return connection;
    }

    private static void insert(Connection connection, String name, String uuid, String data) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO easyauth VALUES (?, ?, ?, ?)")) {
            statement.setString(1, name);
            statement.setString(2, name.toLowerCase(java.util.Locale.ROOT));
            statement.setString(3, uuid);
            statement.setString(4, data);
            statement.executeUpdate();
        }
    }

    private static int countRows(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var results = statement.executeQuery("SELECT COUNT(*) FROM easyauth")) {
            results.next();
            return results.getInt(1);
        }
    }

    private static IdentityClaim claim() {
        return new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, NAME, VERIFIED, VERIFIED,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.UNKNOWN, true, false, false));
    }

    private void set(String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    public static final class EasyAuth {
        public static SQLite DB;
        public static final ExtendedConfig extendedConfig = new ExtendedConfig();
    }

    public static final class ExtendedConfig {
        public boolean allowCaseInsensitiveUsername;
    }

    public static final class Cache {
        static Entry entry;

        public static Entry get(String name) {
            assertEquals(NAME, name);
            return entry;
        }
    }

    public static final class Entry {
        public String username;
        public String password = "";
        public UUID uuid;
        public String onlineAccount;

        public Entry(String name, String lowerName, String uuid, String data) {
            PARSED.add(name);
            username = name;
            this.uuid = uuid == null ? null : UUID.fromString(uuid);
            onlineAccount = JsonParser.parseString(data).getAsJsonObject().get("onlineAccount").getAsString();
        }
    }
}
