/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EasyAuthBedrockTest {
    private EasyAuth344Bridge bridge;
    private MinecraftServer server;
    private ServerPlayer player;
    private PlayerAuth auth;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setup() throws Exception {
        EasyAuth.DB = new Database();
        EasyAuth.config = new Config();
        Cache.entry = new Entry();
        EasyAuth.DB.stored = new Entry();
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        inject("easyAuth", EasyAuth.class);
        inject("entryType", Entry.class);
        inject("playerAuth", PlayerAuth.class);
        inject("playersCache", Cache.class);
        inject("ipLimit", IpLimit.class);
        server = mock(MinecraftServer.class);
        when(server.submit(any(Supplier.class))).thenAnswer(call -> {
            try {
                return CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(0)).get());
            } catch (Exception error) {
                return CompletableFuture.failedFuture(error);
            }
        });
        player = mock(ServerPlayer.class, withSettings().extraInterfaces(PlayerAuth.class));
        player.connection = mock(ServerGamePacketListenerImpl.class);
        player.connection.player = player;
        when(player.connection.isAcceptingMessages()).thenReturn(true);
        when(player.getGameProfile()).thenReturn(new GameProfile(FabricBedrockLoginTest.UUID_B, ".Name"));
        when(player.getUUID()).thenReturn(FabricBedrockLoginTest.UUID_B);
        auth = (PlayerAuth) player;
        when(auth.easyAuth$getPlayerEntryV1()).thenReturn(Cache.entry);
        when(auth.easyAuth$getIpAddress()).thenReturn("127.0.0.2");
        doAnswer(call -> {
            assertEquals(1, EasyAuth.DB.writes);
            when(auth.easyAuth$isAuthenticated()).thenReturn(true);
            return null;
        }).when(auth).easyAuth$setAuthenticated(true);
    }

    private void inject(String name, Class<?> type) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, type);
    }

    private EasyAuth344Bridge.BedrockOwner prepare() {
        return bridge.beforeBedrock(server, ".Name", FabricBedrockLoginTest.UUID_B);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "FALSE"})
    void completedMatchingAccountPersistsBeforeUnlockWithoutChangingIdentityOrCredential(String kind) {
        Cache.entry.onlineAccount = Kind.valueOf(kind);
        EasyAuth.DB.stored.onlineAccount = Kind.valueOf(kind);
        var owner = prepare();
        assertNotNull(owner);
        try (var messages = mockStatic(FabricMessages.class)) {
            bridge.onBedrockJoin(player, owner);
        }
        assertEquals(1, EasyAuth.DB.writes);
        verify(auth).easyAuth$restoreTrueLocation();
        verify(auth).easyAuth$setAuthenticated(true);
        assertEquals(Kind.valueOf(kind), Cache.entry.onlineAccount);
        assertEquals(FabricBedrockLoginTest.UUID_B, Cache.entry.uuid);
        assertEquals("hash", Cache.entry.password);
        assertEquals(0L, Cache.entry.loginTries);
        assertEquals("127.0.0.2", Cache.entry.lastIp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "empty", "null-password", "uuid", "java", "premium", "forced", "name", "null-uuid"})
    void ineligibleDurableAccountsNeverPrepare(String problem) {
        switch (problem) {
            case "absent" -> EasyAuth.DB.stored = null;
            case "empty" -> EasyAuth.DB.stored.password = "";
            case "null-password" -> EasyAuth.DB.stored.password = null;
            case "uuid" -> EasyAuth.DB.stored.uuid = UUID.randomUUID();
            case "java" -> EasyAuth.DB.stored.uuid = UUID.nameUUIDFromBytes("OfflinePlayer:.Name".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "premium" -> EasyAuth.DB.stored.onlineAccount = Kind.TRUE;
            case "forced" -> EasyAuth.DB.stored.forcedUuid = FabricBedrockLoginTest.UUID_B.toString();
            case "name" -> EasyAuth.DB.stored.username = "Other";
            case "null-uuid" -> EasyAuth.DB.stored.uuid = null;
            default -> throw new AssertionError(problem);
        }
        assertNull(prepare());
        assertEquals(0, EasyAuth.DB.writes);
        verify(auth, never()).easyAuth$setAuthenticated(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cache-replaced", "player-entry-replaced", "password-changed", "row-absent", "uuid-changed", "disconnected", "authenticated", "actual-uuid"})
    void joinRechecksDurableOwnerAndExactCachedEntry(String change) {
        var owner = prepare();
        switch (change) {
            case "cache-replaced" -> Cache.entry = new Entry();
            case "player-entry-replaced" -> when(auth.easyAuth$getPlayerEntryV1()).thenReturn(new Entry());
            case "password-changed" -> EasyAuth.DB.stored.password = "other-hash";
            case "row-absent" -> EasyAuth.DB.stored = null;
            case "uuid-changed" -> EasyAuth.DB.stored.uuid = UUID.randomUUID();
            case "disconnected" -> when(player.connection.isAcceptingMessages()).thenReturn(false);
            case "authenticated" -> when(auth.easyAuth$isAuthenticated()).thenReturn(true);
            case "actual-uuid" -> when(player.getUUID()).thenReturn(UUID.randomUUID());
            default -> throw new AssertionError(change);
        }
        assertThrows(RuntimeException.class, () -> bridge.onBedrockJoin(player, owner));
        assertEquals(0, EasyAuth.DB.writes);
        verify(auth, never()).easyAuth$setAuthenticated(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "throw"})
    void storageFailureRestoresLoginMetadataAndDoesNotUnlock(String failure) {
        var owner = prepare();
        ZonedDateTime oldDate = Cache.entry.lastAuthenticatedDate;
        EasyAuth.DB.failure = failure;
        assertThrows(RuntimeException.class, () -> bridge.onBedrockJoin(player, owner));
        assertEquals(oldDate, Cache.entry.lastAuthenticatedDate);
        assertEquals("127.0.0.1", Cache.entry.lastIp);
        assertEquals(2L, Cache.entry.loginTries);
        assertEquals(Kind.UNKNOWN, Cache.entry.onlineAccount);
        assertEquals("hash", Cache.entry.password);
        verify(auth, never()).easyAuth$setAuthenticated(true);
        verify(auth, never()).easyAuth$restoreTrueLocation();
    }

    @Test
    void nativeFloodgateRemainsDisabledAndCannotSubstituteForThisPath() {
        EasyAuth.config.floodgateAutoLogin = true;
        assertThrows(RuntimeException.class, this::prepare);
        verifyNoInteractions(server);
    }

    @Test
    void accountReadFailureDoesNotUseCachedOwner() {
        EasyAuth.DB.readFailure = true;
        assertThrows(RuntimeException.class, this::prepare);
        assertEquals(0, EasyAuth.DB.writes);
    }

    @Test
    void closedStorageAndActiveLockCannotUnlock() {
        EasyAuth.DB.closed = true;
        assertThrows(RuntimeException.class, this::prepare);
        EasyAuth.DB.closed = false;
        var owner = prepare();
        Cache.entry.lastKickedDate = ZonedDateTime.now();
        assertThrows(RuntimeException.class, () -> bridge.onBedrockJoin(player, owner));
        assertEquals(0, EasyAuth.DB.writes);
        verify(auth, never()).easyAuth$setAuthenticated(true);
    }

    @Test
    void unavailableOwnershipMemberFailsClosed() {
        assertThrows(IllegalStateException.class, () -> BedrockAccount.read(Object.class, new Object()));
    }

    @Test
    void failureAfterSaveDoesNotDeleteOrConvertAccount() {
        var owner = prepare();
        doThrow(new IllegalStateException("restore failed")).when(auth).easyAuth$restoreTrueLocation();
        assertThrows(RuntimeException.class, () -> bridge.onBedrockJoin(player, owner));
        assertEquals(1, EasyAuth.DB.writes);
        assertEquals(Kind.UNKNOWN, Cache.entry.onlineAccount);
        assertEquals("hash", Cache.entry.password);
        assertEquals(FabricBedrockLoginTest.UUID_B, Cache.entry.uuid);
        verify(auth, never()).easyAuth$setAuthenticated(true);
    }

    @Test
    void cacheReplacementDuringPersistenceCannotUnlockOldPlayer() {
        var owner = prepare();
        EasyAuth.DB.afterSave = () -> Cache.entry = new Entry();
        assertThrows(RuntimeException.class, () -> bridge.onBedrockJoin(player, owner));
        verify(auth, never()).easyAuth$setAuthenticated(true);
        assertEquals(1, EasyAuth.DB.writes);
    }

    public enum Kind { TRUE, FALSE, UNKNOWN }

    public static class Entry {
        public String username = ".Name";
        public UUID uuid = FabricBedrockLoginTest.UUID_B;
        public String password = "hash";
        public Kind onlineAccount = Kind.UNKNOWN;
        public String forcedUuid;
        public ZonedDateTime lastKickedDate = ZonedDateTime.parse("1970-01-01T00:00:00Z");
        public ZonedDateTime lastAuthenticatedDate = lastKickedDate;
        public long loginTries = 2;
        public String lastIp = "127.0.0.1";
    }

    public interface PlayerAuth {
        boolean easyAuth$isAuthenticated();
        void easyAuth$setAuthenticated(boolean value);
        Object easyAuth$getPlayerEntryV1();
        String easyAuth$getIpAddress();
        void easyAuth$restoreTrueLocation();
    }

    public static class EasyAuth {
        public static Database DB;
        public static Config config;
        public static final Extended extendedConfig = new Extended();
    }

    public static class Config {
        public boolean premiumAutoLogin;
        public boolean offlineByDefault;
        public boolean floodgateAutoLogin;
        public boolean enableGlobalPassword;
        public boolean singleUseGlobalPassword;
        public long sessionTimeout = -1;
        public long resetLoginAttemptsTimeout = 30;
    }

    public static class Extended {
        public boolean skipAllAuthChecks;
        public boolean allowAllPackets;
    }

    // Deliberately no registration/deletion API. Such a call would fail these tests.
    public static class Database {
        Entry stored;
        int writes;
        String failure;
        boolean readFailure;
        boolean closed;
        Runnable afterSave = () -> { };
        public boolean isClosed() { return closed; }
        public Entry getUserData(String name) {
            if (readFailure) { throw new IllegalStateException("unreadable"); }
            return stored;
        }
        public boolean updateUserData(Entry entry) {
            writes++;
            if ("throw".equals(failure)) { throw new IllegalStateException("write failure"); }
            afterSave.run();
            return !"false".equals(failure);
        }
    }

    public static class Cache {
        static Entry entry;
        public static Entry get(String name) { return entry; }
    }

    public static class IpLimit {
        public static void invalidateCache(String ip) { }
    }
}
