/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class RenameFollowBridgeTest {
    private static final String NAME = "NewName";
    private static final String OLD_NAME = "OldName";
    private static final UUID VERIFIED = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final List<String> EVENTS = new ArrayList<>();
    private EasyAuth344Bridge bridge;
    private MinecraftServer server;
    private Entry current;
    private Entry old;

    @BeforeEach
    void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EVENTS.clear();
        EasyAuth.DB = new Database();
        Cache.entries.clear();
        Cache.failPut = false;
        current = new Entry(NAME);
        old = new Entry(OLD_NAME);
        old.uuid = VERIFIED;
        old.onlineAccount = Kind.TRUE;
        old.lastIp = "127.0.0.2";
        old.lastAuthenticatedDate = old.lastAuthenticatedDate.plusDays(10);
        old.lastKickedDate = old.lastKickedDate.plusDays(5);
        old.registrationDate = old.registrationDate.minusDays(7);
        old.loginTries = 9;
        old.dataVersion = 7;
        old.forcedUuid = "keep-me";
        EasyAuth.DB.rows.put(NAME, copy(current));
        EasyAuth.DB.rows.put(OLD_NAME, copy(old));
        Cache.entries.put(NAME, current);
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
    void savesAllStateBeforeCachePutAndOldRowDeleteThenRereadsStatus() throws Exception {
        // The migrated account stays BOUND even when automatic creation of new accounts is disabled.
        bridge.beforeConfigure(server, claim(IdentityClaim.Decision.PREMIUM_VERIFIED));

        assertEquals(List.of("scan", "update", "put", "delete", "read:" + NAME), EVENTS);
        assertEquals(state(old), state(current));
        assertEquals(state(old), state(EasyAuth.DB.rows.get(NAME)));
        assertEquals(NAME, current.username);
        assertEquals("newname", current.usernameLowerCase);
        assertSame(current, Cache.entries.get(NAME));
        assertFalse(EasyAuth.DB.rows.containsKey(OLD_NAME));
        assertEquals(new AccountState.Snapshot(AccountState.BOUND, VERIFIED), bridge.accountStatus(NAME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "throw", "missing-row"})
    void failedUpdateRestoresEveryLiveFieldAndPreservesOldRow(String failure) throws Exception {
        List<Object> original = state(current);
        EasyAuth.DB.updateFailure = failure;
        if ("missing-row".equals(failure)) {
            EasyAuth.DB.rows.remove(NAME);
        }

        assertFailure("rename-failed");

        assertEquals(original, state(current));
        assertEquals(state(old), state(EasyAuth.DB.rows.get(OLD_NAME)));
        if (!"missing-row".equals(failure)) {
            assertEquals(original, state(EasyAuth.DB.rows.get(NAME)));
        }
        assertSame(current, Cache.entries.get(NAME));
        assertEquals(List.of("scan", "update"), EVENTS);
    }

    @Test
    void duplicateSourcesRejectWithoutWritingOrChangingTheCache() throws Exception {
        List<Object> original = state(current);
        Entry duplicate = copy(old);
        duplicate.username = "Duplicate";
        EasyAuth.DB.rows.put(duplicate.username, duplicate);

        assertFailure("rename-conflict");

        assertEquals(List.of("scan"), EVENTS);
        assertEquals(original, state(current));
        assertEquals(3, EasyAuth.DB.rows.size());
    }

    @Test
    void passwordAccountKeepsItsPasswordAndSkipsTheRenameLookup() {
        current.password = "existing-hash";
        current.onlineAccount = Kind.FALSE;
        current.uuid = offlineUuid();
        EasyAuth.DB.rows.put(NAME, copy(current));

        bridge.beforeConfigure(server, claim(IdentityClaim.Decision.PREMIUM_VERIFIED));

        assertEquals(List.of("read:" + NAME), EVENTS);
        assertEquals("existing-hash", current.password);
        assertTrue(EasyAuth.DB.rows.containsKey(OLD_NAME));
    }

    @Test
    void unverifiedConnectionNeverScansOrMigrates() {
        bridge.beforeConfigure(server, claim(IdentityClaim.Decision.OFFLINE));

        assertEquals(List.of("read:" + NAME), EVENTS);
        assertEquals(Kind.UNKNOWN, current.onlineAccount);
        assertTrue(EasyAuth.DB.rows.containsKey(OLD_NAME));
    }

    @Test
    void alreadyBoundCurrentNameSkipsLookupEvenWithAnOldDuplicate() {
        current.uuid = VERIFIED;
        current.onlineAccount = Kind.TRUE;
        EasyAuth.DB.rows.put(NAME, copy(current));

        bridge.beforeConfigure(server, claim(IdentityClaim.Decision.PREMIUM_VERIFIED));

        assertEquals(List.of("read:" + NAME), EVENTS);
        assertTrue(EasyAuth.DB.rows.containsKey(OLD_NAME));
    }

    @Test
    void emptyScanKeepsTheExistingFirstLoginPath() {
        EasyAuth.DB.emptyScan = true;

        bridge.beforeConfigure(server, claim(IdentityClaim.Decision.PREMIUM_VERIFIED));

        assertEquals(List.of("scan", "read:" + NAME), EVENTS);
        assertEquals(Kind.UNKNOWN, current.onlineAccount);
        assertTrue(EasyAuth.DB.rows.containsKey(OLD_NAME));
    }

    @Test
    void missingCachePutRejectsBeforeAnyMutationOrWrite() throws Exception {
        set("playersCache", CacheWithoutPut.class);
        List<Object> original = state(current);

        assertFailure("rename-failed");

        assertEquals(List.of("scan"), EVENTS);
        assertEquals(original, state(current));
        assertEquals(2, EasyAuth.DB.rows.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deleteFailureLeavesTheNewAccountUsableAndOldRowIntact(boolean throwsException) throws Exception {
        EasyAuth.DB.failDelete = true;
        EasyAuth.DB.throwDelete = throwsException;

        bridge.beforeConfigure(server, claim(IdentityClaim.Decision.PREMIUM_VERIFIED));

        assertEquals(List.of("scan", "update", "put", "delete", "read:" + NAME), EVENTS);
        assertEquals(state(old), state(EasyAuth.DB.rows.get(NAME)));
        assertEquals(state(old), state(EasyAuth.DB.rows.get(OLD_NAME)));
        assertSame(current, Cache.entries.get(NAME));
    }

    @Test
    void cachePutFailureKeepsBothDurableRowsAndRejectsWithoutDeleting() throws Exception {
        Cache.failPut = true;

        assertFailure("rename-failed");

        assertEquals(List.of("scan", "update", "put"), EVENTS);
        assertEquals(state(old), state(EasyAuth.DB.rows.get(NAME)));
        assertEquals(state(old), state(EasyAuth.DB.rows.get(OLD_NAME)));
        assertEquals(state(old), state(current));
    }

    private void assertFailure(String key) {
        Throwable failure = assertThrows(IllegalStateException.class,
                () -> bridge.beforeConfigure(server, claim(IdentityClaim.Decision.PREMIUM_VERIFIED)));
        while (!(failure instanceof FabricMessages.PlayerFailure) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        assertInstanceOf(FabricMessages.PlayerFailure.class, failure);
        assertEquals(key, failure.getMessage());
    }

    private static IdentityClaim claim(IdentityClaim.Decision decision) {
        boolean verified = decision == IdentityClaim.Decision.PREMIUM_VERIFIED;
        return new IdentityClaim(decision, NAME, verified ? VERIFIED : offlineUuid(), verified ? VERIFIED : null,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.UNKNOWN, true, false, false));
    }

    private static UUID offlineUuid() {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void set(String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    private static List<Object> state(Entry entry) throws Exception {
        List<Object> values = new ArrayList<>();
        for (Field field : Entry.class.getFields()) {
            if (!field.getName().startsWith("username")) {
                values.add(field.get(entry));
            }
        }
        return values;
    }

    private static Entry copy(Entry entry) {
        Entry result = new Entry(entry.username);
        try {
            for (Field field : Entry.class.getFields()) {
                field.set(result, field.get(entry));
            }
            return result;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static final class EasyAuth {
        public static Database DB;
    }

    public static final class Cache {
        static final Map<String, Entry> entries = new HashMap<>();
        static boolean failPut;

        public static Entry get(String name) {
            return entries.get(name);
        }

        public static void put(String name, Entry entry) {
            EVENTS.add("put");
            assertEquals(Kind.TRUE, EasyAuth.DB.rows.get(name).onlineAccount);
            assertTrue(EasyAuth.DB.rows.containsKey(OLD_NAME));
            if (failPut) {
                throw new IllegalStateException("cache unavailable");
            }
            entries.put(name, entry);
        }
    }

    public static final class CacheWithoutPut {
        public static Entry get(String name) {
            return Cache.get(name);
        }
    }

    public static final class Database {
        final Map<String, Entry> rows = new HashMap<>();
        String updateFailure = "";
        boolean emptyScan;
        boolean failDelete;
        boolean throwDelete;

        public Map<String, Entry> getAllData() {
            EVENTS.add("scan");
            return emptyScan ? Map.of() : new HashMap<>(rows);
        }

        public Entry getUserData(String name) {
            EVENTS.add("read:" + name);
            Entry entry = rows.get(name);
            return entry == null ? null : copy(entry);
        }

        public boolean updateUserData(Entry entry) {
            EVENTS.add("update");
            if ("throw".equals(updateFailure)) {
                throw new IllegalStateException("storage unavailable");
            }
            if ("false".equals(updateFailure) || !rows.containsKey(entry.username)) {
                return false;
            }
            rows.put(entry.username, copy(entry));
            return true;
        }

        public boolean deleteUserData(String name) {
            EVENTS.add("delete");
            assertEquals("put", EVENTS.get(EVENTS.size() - 2));
            assertEquals(Kind.TRUE, Cache.entries.get(NAME).onlineAccount);
            if (throwDelete) {
                throw new IllegalStateException("delete unavailable");
            }
            return !failDelete && rows.remove(name) != null;
        }
    }

    public enum Kind {
        UNKNOWN, FALSE, TRUE
    }

    public static final class Entry {
        public String username;
        public String usernameLowerCase;
        public UUID uuid;
        public String password = "";
        public String lastIp = "127.0.0.1";
        public ZonedDateTime lastAuthenticatedDate = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        public long loginTries;
        public ZonedDateTime lastKickedDate = lastAuthenticatedDate;
        public Kind onlineAccount = Kind.UNKNOWN;
        public ZonedDateTime registrationDate = lastAuthenticatedDate;
        public int dataVersion = 1;
        public String forcedUuid;

        Entry(String name) {
            username = name;
            usernameLowerCase = name.toLowerCase(Locale.ROOT);
        }
    }
}
