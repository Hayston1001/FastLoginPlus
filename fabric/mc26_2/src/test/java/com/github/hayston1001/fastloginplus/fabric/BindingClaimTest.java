/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class BindingClaimTest {
    private static final String NAME = "TestUser";
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private EasyAuth344Bridge bridge;
    private Entry entry;
    private ServerPlayer player;
    private PlayerAuth auth;
    private AtomicBoolean authenticated;

    @BeforeEach
    void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyAuth.DB = new Database();
        entry = EasyAuth.DB.entry;
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        set(bridge, "easyAuth", EasyAuth.class);
        set(bridge, "entryType", Entry.class);
        set(bridge, "playerAuth", PlayerAuth.class);
        set(bridge, "ipLimit", IpLimit.class);
        set(bridge, "playersCache", Cache.class);

        player = mock(ServerPlayer.class, withSettings().extraInterfaces(PlayerAuth.class));
        player.connection = mock(ServerGamePacketListenerImpl.class);
        player.connection.player = player;
        when(player.getGameProfile()).thenReturn(new GameProfile(TARGET, NAME));
        auth = (PlayerAuth) player;
        authenticated = new AtomicBoolean();
        when(auth.easyAuth$isAuthenticated()).thenAnswer(call -> authenticated.get());
        when(auth.easyAuth$getPlayerEntryV1()).thenReturn(entry);
        when(auth.easyAuth$getIpAddress()).thenReturn("127.0.0.2");
        doAnswer(call -> {
            assertTrue(EasyAuth.DB.writes > 0);
            authenticated.set(call.getArgument(0));
            return null;
        }).when(auth).easyAuth$setAuthenticated(true);
    }

    @ParameterizedTest
    @EnumSource(IdentityClaim.Consent.class)
    void verifiedConnectionClaimsOnlyWithRecordedConsentWithoutAnyQueue(IdentityClaim.Consent consent) {

        bridge.onJoin(player, claim(consent));

        boolean accepted = consent == IdentityClaim.Consent.YES;
        assertEquals(accepted, authenticated.get());
        assertEquals(accepted ? 1 : 0, EasyAuth.DB.writes);
        assertEquals("old-hash", entry.password);
        assertEquals(accepted ? TARGET : OFFLINE, entry.uuid);
        assertEquals(accepted ? Kind.TRUE : Kind.FALSE, entry.onlineAccount);
        assertEquals(accepted ? AccountState.BOUND : AccountState.PASSWORD, bridge.accountStatus(NAME).state());
        if (accepted) {
            verify(auth).easyAuth$restoreTrueLocation();
        } else {
            verify(auth, never()).easyAuth$restoreTrueLocation();
            verify(auth, never()).easyAuth$setAuthenticated(true);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "throw"})
    void saveFailureRollsBackAndDisconnectsWithoutFinishingSession(String failure) {
        EasyAuth.DB.failure = failure;
        joinThroughEntrypoint(claim(IdentityClaim.Consent.YES));
        assertEquals(1, EasyAuth.DB.writes);
        assertEquals("old-hash", entry.password);
        assertEquals(OFFLINE, entry.uuid);
        assertEquals(Kind.FALSE, entry.onlineAccount);
        assertEquals(ZonedDateTime.parse("2026-01-01T00:00:00Z"), entry.lastAuthenticatedDate);
        assertEquals(2, entry.loginTries);
        assertEquals("127.0.0.1", entry.lastIp);
        assertFalse(authenticated.get());
        verify(auth, never()).easyAuth$restoreTrueLocation();
        verify(auth, never()).easyAuth$setAuthenticated(true);
        verify(player.connection).disconnect(argThat((Component message) -> message.getString().equals(
                FabricMessages.text("binding-save-failed").getString())));
    }

    @Test
    void postSaveCompletionFailureKeepsDormantPasswordAndDisconnectsForReconnect() {
        doAnswer(call -> {
            throw new IllegalStateException("restore unavailable");
        }).when(auth).easyAuth$restoreTrueLocation();
        bridge.onJoin(player, claim(IdentityClaim.Consent.YES));
        assertEquals(1, EasyAuth.DB.writes);
        assertEquals("old-hash", entry.password);
        assertEquals(TARGET, entry.uuid);
        assertEquals(AccountState.BOUND, bridge.accountStatus(NAME).state());
        assertFalse(authenticated.get());
        verify(auth, never()).easyAuth$setAuthenticated(true);
        verify(player.connection).disconnect(argThat((Component message) -> message.getString().equals(
                FabricMessages.text("binding-saved-reconnect").getString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "old-hash"})
    void existingBoundAccountLogsInWithMatchingUuidRegardlessOfPasswordOrConsent(String password) {
        entry.onlineAccount = Kind.TRUE;
        entry.uuid = TARGET;
        entry.password = password;
        bridge.onJoin(player, claim(IdentityClaim.Consent.NO));
        assertTrue(authenticated.get());
        assertEquals(password, entry.password);
        assertEquals(TARGET, entry.uuid);
        verify(player.connection, never()).disconnect(any(Component.class));
    }

    @Test
    void retryUsesSavedBoundAccountAndDoesNotClearTheRetainedPassword() {
        bridge.onJoin(player, claim(IdentityClaim.Consent.YES));
        authenticated.set(false);
        bridge.onJoin(player, claim(IdentityClaim.Consent.UNKNOWN));
        assertTrue(authenticated.get());
        assertEquals(2, EasyAuth.DB.writes);
        assertEquals("old-hash", entry.password);
        assertEquals(Kind.TRUE, entry.onlineAccount);
    }

    @Test
    void duplicateJoinOnAuthenticatedConnectionCannotWriteAgain() {
        bridge.onJoin(player, claim(IdentityClaim.Consent.YES));
        assertThrows(IllegalStateException.class, () -> bridge.onJoin(player, claim(IdentityClaim.Consent.YES)));
        assertEquals(1, EasyAuth.DB.writes);
        assertEquals("old-hash", entry.password);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configurationChecksAllowPasswordConversionAndDormantBoundPasswordWithoutWriting(boolean bound)
            throws Exception {
        if (bound) {
            entry.onlineAccount = Kind.TRUE;
            entry.uuid = TARGET;
        }
        configure(claim(IdentityClaim.Consent.YES));
        assertEquals(0, EasyAuth.DB.writes);
        assertFalse(authenticated.get());
        assertEquals("old-hash", entry.password);
    }

    @Test
    void unverifiedBoundAccountRejectsDespiteRetainedPassword() {
        entry.onlineAccount = Kind.TRUE;
        entry.uuid = TARGET;
        var offline = new IdentityClaim(IdentityClaim.Decision.OFFLINE, NAME, OFFLINE, null,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, true, false, false));
        var failure = assertThrows(FabricMessages.PlayerFailure.class, () -> bridge.onJoin(player, offline));
        assertEquals("ownership-conflict", failure.getMessage());
        assertEquals(0, EasyAuth.DB.writes);
        assertFalse(authenticated.get());
        assertEquals("old-hash", entry.password);
    }

    @Test
    void mismatchedBoundUuidRejectsWithDormantPassword() {
        entry.onlineAccount = Kind.TRUE;
        entry.uuid = UUID.randomUUID();
        assertThrows(FabricMessages.PlayerFailure.class, () -> bridge.onJoin(player, claim(IdentityClaim.Consent.YES)));
        assertEquals(0, EasyAuth.DB.writes);
        assertFalse(authenticated.get());
    }

    @Test
    void unclaimedLiveEntryStillRequiresAnEmptyPassword() {
        entry.onlineAccount = Kind.UNKNOWN;
        entry.password = "";
        Entry live = new Entry();
        live.onlineAccount = Kind.UNKNOWN;
        when(auth.easyAuth$getPlayerEntryV1()).thenReturn(live);
        var failure = assertThrows(FabricMessages.PlayerFailure.class,
                () -> bridge.onJoin(player, claim(IdentityClaim.Consent.YES, true)));
        assertEquals("ownership-conflict", failure.getMessage());
        assertEquals("old-hash", live.password);
        assertEquals(0, EasyAuth.DB.writes);
        assertFalse(authenticated.get());
    }

    private void configure(IdentityClaim claim) {
        MinecraftServer server = mock(MinecraftServer.class);
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
        bridge.beforeConfigure(server, claim);
    }

    private void joinThroughEntrypoint(IdentityClaim claim) {
        try (var fabric = mockStatic(FastLoginFabric.class, CALLS_REAL_METHODS)) {
            fabric.when(() -> FastLoginFabric.parseClaim(player.getGameProfile())).thenReturn(claim);
            FastLoginFabric.onJoin(player.connection, bridge, mock(FabricCrackedNotice.class));
        }
    }

    private IdentityClaim claim(IdentityClaim.Consent consent) {
        return claim(consent, false);
    }

    private IdentityClaim claim(IdentityClaim.Consent consent, boolean autoRegisterUnknown) {
        return new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, NAME, TARGET, TARGET,
                new IdentityClaim.Facts(false, false, consent, true, autoRegisterUnknown, false));
    }

    private static void set(EasyAuth344Bridge bridge, String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    public interface PlayerAuth {
        boolean easyAuth$isAuthenticated();

        Entry easyAuth$getPlayerEntryV1();

        String easyAuth$getIpAddress();

        void easyAuth$setAuthenticated(boolean authenticated);

        void easyAuth$restoreTrueLocation();
    }

    public static final class EasyAuth {
        public static Database DB;
        public static final Config config = new Config();
    }

    public static final class Config {
        public long resetLoginAttemptsTimeout = 30;
    }

    public static final class IpLimit {
        public static void invalidateCache(String ip) {
        }
    }

    public static final class Database {
        final Entry entry = new Entry();
        int writes;
        String failure = "";

        public Entry getUserData(String name) {
            return NAME.equals(name) ? entry : null;
        }

        public boolean updateUserData(Entry candidate) {
            assertEquals(entry, candidate);
            writes++;
            if ("throw".equals(failure)) {
                throw new IllegalStateException("storage unavailable");
            }
            if ("false".equals(failure)) {
                return false;
            }
            return true;
        }
    }

    public enum Kind {
        FALSE, TRUE, UNKNOWN
    }

    public static final class Cache {
        public static Entry get(String name) {
            return EasyAuth.DB.getUserData(name);
        }
    }

    public static final class Entry {
        public String username = NAME;
        public String password = "old-hash";
        public UUID uuid = OFFLINE;
        public Kind onlineAccount = Kind.FALSE;
        public ZonedDateTime lastKickedDate = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        public ZonedDateTime lastAuthenticatedDate = lastKickedDate;
        public String lastIp = "127.0.0.1";
        public long loginTries = 2;
    }
}
