/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CrackedDeleteBridgeTest {
    private static final String NAME = "TargetUser";
    private static final String PROXY = "00000000-0000-0000-0000-000000000003";
    private static final UUID VERIFIED = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private final List<String> feedback = new ArrayList<>();
    private EasyAuth344Bridge bridge;
    private ServerPlayer carrier;

    @BeforeEach
    void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyAuth.DB = new Database();
        Cache.entry = new Entry();
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        set("easyAuth", EasyAuth.class);
        set("entryType", Entry.class);
        set("playersCache", Cache.class);
        set("pendingCrackedReleases", new ConcurrentHashMap<>());
        carrier = mock(ServerPlayer.class, org.mockito.Mockito.withSettings().extraInterfaces(Authentication.class));
        carrier.connection = mock(ServerGamePacketListenerImpl.class);
        when(carrier.connection.isAcceptingMessages()).thenReturn(true);
        when(carrier.getGameProfile()).thenReturn(new GameProfile(VERIFIED, NAME));
        when(((Authentication) carrier).easyAuth$isAuthenticated()).thenReturn(true);
        org.mockito.Mockito.doAnswer(call -> {
            feedback.add(call.getArgument(0, Component.class).getString());
            return null;
        }).when(carrier).sendSystemMessage(org.mockito.ArgumentMatchers.any(Component.class));
    }

    @ParameterizedTest
    @CsvSource({"false,distinct", "true,distinct", "false,absent", "true,absent", "false,same", "true,same"})
    void boundUnbindSendsBeforeWriteRetainsPasswordAndUpdatesCache(boolean self, String cacheKind) {
        Cache.entry = "absent".equals(cacheKind) ? null : "same".equals(cacheKind) ? EasyAuth.DB.entry : Cache.entry;
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            toggle.when(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, self, PROXY))
                    .thenAnswer(call -> {
                        assertBound(EasyAuth.DB.entry);
                        if (Cache.entry != null) {
                            assertBound(Cache.entry);
                        }
                        assertEquals(0, EasyAuth.DB.writes);
                        EasyAuth.DB.sent = true;
                        return null;
                    });
            if (self) {
                bridge.cracked(carrier);
            } else {
                cracked();
            }
            toggle.verify(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, self, PROXY));
            assertEquals(1, EasyAuth.DB.writes);
            assertPassword(EasyAuth.DB.entry);
            if (Cache.entry != null) {
                assertPassword(Cache.entry);
            }
            assertFeedback(self ? "cracked-requested" : "admin-cracked-requested");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "throw"})
    void failedLocalWriteRestoresDurableCandidateAndCacheAfterSending(String failure) {
        EasyAuth.DB.failure = failure;
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            toggle.when(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY))
                    .thenAnswer(call -> { EasyAuth.DB.sent = true; return null; });
            cracked();
            toggle.verify(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY));
            assertEquals(1, EasyAuth.DB.writes);
            assertBound(EasyAuth.DB.entry);
            assertBound(Cache.entry);
            assertFeedback("cracked-save-failed");
            assertEquals(AccountState.BOUND, bridge.accountStatus(NAME).state());
        }
    }

    @Test
    void repeatedCommandOnlyUnbindsLocallyOnce() {
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            toggle.when(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY))
                    .thenAnswer(call -> { EasyAuth.DB.sent = true; return null; });
            cracked();
            cracked();
            toggle.verify(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY),
                    org.mockito.Mockito.times(2));
            assertEquals(1, EasyAuth.DB.writes);
            assertPassword(EasyAuth.DB.entry);
            assertPassword(Cache.entry);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyPasswordFirstCallWarnsWithoutSendingOrWriting(boolean self) {
        emptyPassword();
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            if (self) {
                bridge.cracked(carrier);
            } else {
                cracked();
            }
            toggle.verifyNoInteractions();
            assertFeedback("cracked-release-confirm");
            assertEquals(0, EasyAuth.DB.writes);
            assertEquals(Kind.TRUE, EasyAuth.DB.entry.onlineAccount);
            assertEquals(VERIFIED, EasyAuth.DB.entry.uuid);
            assertEquals(Kind.TRUE, Cache.entry.onlineAccount);
            assertEquals(VERIFIED, Cache.entry.uuid);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,distinct", "true,distinct", "false,absent", "true,absent", "false,same", "true,same"})
    void confirmedEmptyPasswordReleasesToOpenAfterSending(boolean self, String cacheKind) throws Exception {
        emptyPassword();
        Cache.entry = "absent".equals(cacheKind) ? null : "same".equals(cacheKind) ? EasyAuth.DB.entry : Cache.entry;
        Entry original = EasyAuth.DB.entry;
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            toggle.when(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, self, PROXY))
                    .thenAnswer(call -> {
                        assertEquals(Kind.TRUE, EasyAuth.DB.entry.onlineAccount);
                        assertEquals(VERIFIED, EasyAuth.DB.entry.uuid);
                        assertEquals(0, EasyAuth.DB.writes);
                        if (Cache.entry != null) {
                            assertEquals(Kind.TRUE, Cache.entry.onlineAccount);
                            assertEquals(VERIFIED, Cache.entry.uuid);
                        }
                        EasyAuth.DB.sent = true;
                        return null;
                    });
            if (self) {
                bridge.cracked(carrier);
                feedback.clear();
                bridge.cracked(carrier);
            } else {
                cracked();
                feedback.clear();
                cracked();
            }
            toggle.verify(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, self, PROXY));
            assertSame(original, EasyAuth.DB.entry);
            assertEquals(1, EasyAuth.DB.writes);
            assertOpen(EasyAuth.DB.entry);
            if (Cache.entry != null) {
                assertOpen(Cache.entry);
            }
            assertEquals(AccountState.OPEN, bridge.accountStatus(NAME).state());
            assertEquals(0, pending().size());
            assertFeedback(self ? "cracked-release-requested" : "admin-cracked-release-requested");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "throw"})
    void failedReleaseRestoresBothRowsAndRequiresNewConfirmation(String failure) throws Exception {
        emptyPassword();
        EasyAuth.DB.failure = failure;
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            toggle.when(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY))
                    .thenAnswer(call -> { EasyAuth.DB.sent = true; return null; });
            cracked();
            feedback.clear();
            cracked();
            assertFeedback("cracked-release-failed");
            toggle.verify(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY));
            assertEquals(1, EasyAuth.DB.writes);
            for (Entry entry : List.of(EasyAuth.DB.entry, Cache.entry)) {
                assertEquals(Kind.TRUE, entry.onlineAccount);
                assertEquals(VERIFIED, entry.uuid);
                assertEquals("", entry.password);
            }
            assertEquals(AccountState.BOUND, bridge.accountStatus(NAME).state());
            assertEquals(0, pending().size());
            feedback.clear();
            cracked();
            assertFeedback("cracked-release-confirm");
            assertEquals(1, EasyAuth.DB.writes);
        }
    }

    @Test
    void expiredReleaseConfirmationWarnsAgain() throws Exception {
        emptyPassword();
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            cracked();
            pending().replaceAll((key, time) -> System.nanoTime() - TimeUnit.SECONDS.toNanos(31));
            feedback.clear();
            cracked();
            assertFeedback("cracked-release-confirm");
            toggle.verifyNoInteractions();
            assertEquals(0, EasyAuth.DB.writes);
            assertEquals(1, pending().size());
        }
    }

    @Test
    void releaseConfirmationCannotBeSharedBetweenActors() throws Exception {
        emptyPassword();
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            cracked();
            feedback.clear();
            bridge.adminCracked(carrier, NAME, "OtherAdmin", message -> feedback.add(message.getString()));
            assertFeedback("cracked-release-confirm");
            toggle.verifyNoInteractions();
            assertEquals(0, EasyAuth.DB.writes);
            assertEquals(2, pending().size());
        }
    }

    @Test
    void releaseConfirmationCannotBeSharedBetweenTargets() throws Exception {
        emptyPassword();
        EasyAuth.DB.otherEntry = new Entry();
        EasyAuth.DB.otherEntry.username = "OtherUser";
        EasyAuth.DB.otherEntry.password = "";
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            cracked();
            feedback.clear();
            bridge.adminCracked(carrier, "OtherUser", "Admin", message -> feedback.add(message.getString()));
            assertEquals(List.of(FabricMessages.text("cracked-release-confirm", "{player}", "OtherUser")
                    .getString()), feedback);
            toggle.verifyNoInteractions();
            assertEquals(0, EasyAuth.DB.writes);
            assertEquals(2, pending().size());
        }
    }

    @Test
    void failedProxySendLeavesPasswordlessAccountBoundAndConsumesConfirmation() throws Exception {
        emptyPassword();
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            toggle.when(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY))
                    .thenThrow(new IllegalStateException("send failed"));
            cracked();
            feedback.clear();
            cracked();
            assertFeedback("auth-unavailable");
            assertEquals(0, EasyAuth.DB.writes);
            assertEquals(0, pending().size());
            assertEquals(Kind.TRUE, EasyAuth.DB.entry.onlineAccount);
            assertEquals(VERIFIED, EasyAuth.DB.entry.uuid);
            assertEquals(Kind.TRUE, Cache.entry.onlineAccount);
            assertEquals(VERIFIED, Cache.entry.uuid);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "uuid", "kind", "username"})
    void emptyPasswordCacheMismatchRefusesBeforeConfirmation(String mismatch) throws Exception {
        emptyPassword();
        switch (mismatch) {
            case "password" -> Cache.entry.password = "changed-password";
            case "uuid" -> Cache.entry.uuid = UUID.randomUUID();
            case "kind" -> Cache.entry.onlineAccount = Kind.FALSE;
            case "username" -> Cache.entry.username = "OtherUser";
            default -> throw new IllegalArgumentException(mismatch);
        }
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            cracked();
            assertFeedback("ownership-conflict");
            toggle.verifyNoInteractions();
            assertEquals(0, EasyAuth.DB.writes);
            assertEquals(0, pending().size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSWORD", "OPEN", "MISSING"})
    void unboundStatesOnlySendToggle(String state) {
        setState(state);
        Entry original = EasyAuth.DB.entry;
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            cracked();
            toggle.verify(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY));
            assertSame(original, EasyAuth.DB.entry);
            assertEquals(0, EasyAuth.DB.writes);
            assertBound(Cache.entry);
            assertFeedback("admin-cracked-requested");
        }
    }

    @Test
    void conflictRefusesAndNeverSends() {
        setState("CONFLICT");
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            cracked();
            toggle.verifyNoInteractions();
            assertFeedback("ownership-conflict");
            assertEquals(0, EasyAuth.DB.writes);
        }
    }

    @Test
    void mismatchedCacheRefusesBeforeSending() {
        Cache.entry.password = "changed-password";
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            cracked();
            toggle.verifyNoInteractions();
            assertFeedback("ownership-conflict");
            assertBound(EasyAuth.DB.entry);
        }
    }

    @Test
    void unauthenticatedSelfCannotReadOrSend() {
        when(((Authentication) carrier).easyAuth$isAuthenticated()).thenReturn(false);
        try (var toggle = mockStatic(BindingConsentWriter.class)) {
            bridge.cracked(carrier);
            toggle.verifyNoInteractions();
            assertEquals(0, EasyAuth.DB.reads);
            assertFeedback("cracked-login-required");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"send", "disconnected", "read"})
    void earlyFailuresLeaveBoundFieldsUntouched(String failure) {
        if ("disconnected".equals(failure)) {
            when(carrier.connection.isAcceptingMessages()).thenReturn(false);
        }
        EasyAuth.DB.readFails = "read".equals(failure);
        try (var config = mockStatic(FabricConfig.class); var toggle = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            toggle.when(() -> BindingConsentWriter.sendToggle(carrier, NAME, false, false, PROXY))
                    .thenThrow(new IllegalStateException("send failed"));
            cracked();
            if (!"send".equals(failure)) {
                toggle.verifyNoInteractions();
            }
            assertFeedback("auth-unavailable");
            assertEquals(0, EasyAuth.DB.writes);
            assertBound(EasyAuth.DB.entry);
            assertBound(Cache.entry);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSWORD", "OPEN", "MISSING", "CONFLICT"})
    void deleteOnlySendsProxyRequestForNonBoundStates(String state) {
        setState(state);
        try (var config = mockStatic(FabricConfig.class); var delete = mockStatic(DeleteConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            delete();
            delete.verify(() -> DeleteConsentWriter.send(carrier, NAME, false, PROXY));
            assertFeedback("delete-requested");
            assertEquals(0, EasyAuth.DB.writes);
            assertBound(Cache.entry);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "old-hash"})
    void deleteRefusesAllBoundAccounts(String password) {
        EasyAuth.DB.entry.password = password;
        try (var delete = mockStatic(DeleteConsentWriter.class)) {
            delete();
            delete.verifyNoInteractions();
            assertFeedback("delete-premium-denied");
            assertEquals(0, EasyAuth.DB.writes);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "disconnected", "send"})
    void deleteFailuresDoNotReportRequestedOrWriteLocally(String failure) {
        EasyAuth.DB.readFails = "read".equals(failure);
        when(carrier.connection.isAcceptingMessages()).thenReturn(!"disconnected".equals(failure));
        setState("PASSWORD");
        try (var config = mockStatic(FabricConfig.class); var delete = mockStatic(DeleteConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
            delete.when(() -> DeleteConsentWriter.send(carrier, NAME, false, PROXY))
                    .thenThrow(new IllegalStateException("send failed"));
            delete();
            if (!"send".equals(failure)) {
                delete.verifyNoInteractions();
            }
            assertFeedback("auth-unavailable");
            assertEquals(0, EasyAuth.DB.writes);
            assertPassword(EasyAuth.DB.entry);
        }
    }

    private void cracked() {
        bridge.adminCracked(carrier, NAME, "Admin", message -> feedback.add(message.getString()));
    }

    private static void emptyPassword() {
        EasyAuth.DB.entry.password = "";
        Cache.entry.password = "";
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Long> pending() throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField("pendingCrackedReleases");
        field.setAccessible(true);
        return (Map<Object, Long>) field.get(bridge);
    }

    private void delete() {
        bridge.adminDelete(carrier, NAME, "Admin", message -> feedback.add(message.getString()));
    }

    private void assertFeedback(String key) {
        assertEquals(List.of(FabricMessages.text(key, "{player}", NAME).getString()), feedback);
    }

    private static void setState(String state) {
        if ("MISSING".equals(state)) {
            EasyAuth.DB.entry = null;
        } else {
            EasyAuth.DB.entry.onlineAccount = "PASSWORD".equals(state) ? Kind.FALSE : Kind.UNKNOWN;
            EasyAuth.DB.entry.uuid = "CONFLICT".equals(state) ? VERIFIED : OFFLINE;
            EasyAuth.DB.entry.password = "OPEN".equals(state) ? "" : "old-hash";
        }
    }

    private static void assertBound(Entry entry) {
        assertEquals(Kind.TRUE, entry.onlineAccount);
        assertEquals(VERIFIED, entry.uuid);
        assertEquals("old-hash", entry.password);
    }

    private static void assertPassword(Entry entry) {
        assertEquals(Kind.FALSE, entry.onlineAccount);
        assertEquals(OFFLINE, entry.uuid);
        assertEquals("old-hash", entry.password);
    }

    private static void assertOpen(Entry entry) {
        assertEquals(Kind.UNKNOWN, entry.onlineAccount);
        assertEquals(null, entry.uuid);
        assertEquals("", entry.password);
    }

    private void set(String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    public interface Authentication {
        boolean easyAuth$isAuthenticated();
    }

    public static final class EasyAuth {
        public static Database DB;
    }

    public static final class Cache {
        static Entry entry;

        public static Entry get(String name) {
            if ("OtherUser".equals(name)) {
                return null;
            }
            assertEquals(NAME, name);
            return entry;
        }
    }

    public static final class Database {
        Entry entry = new Entry();
        Entry otherEntry;
        int reads;
        int writes;
        boolean sent;
        boolean readFails;
        String failure;

        public Entry getUserData(String name) {
            if ("OtherUser".equals(name)) {
                return otherEntry;
            }
            assertEquals(NAME, name);
            reads++;
            if (readFails) {
                throw new IllegalStateException("read failed");
            }
            return entry;
        }

        public boolean updateUserData(Entry candidate) {
            assertEquals(true, sent);
            writes++;
            if (candidate.password.isEmpty()) {
                assertOpen(candidate);
                if (Cache.entry != null) {
                    assertOpen(Cache.entry);
                }
            } else {
                assertPassword(candidate);
                if (Cache.entry != null) {
                    assertPassword(Cache.entry);
                }
            }
            if ("throw".equals(failure)) {
                throw new IllegalStateException("write failed");
            }
            return !"false".equals(failure);
        }
    }

    public enum Kind {
        TRUE, FALSE, UNKNOWN
    }

    public static final class Entry {
        public String username = NAME;
        public String password = "old-hash";
        public Kind onlineAccount = Kind.TRUE;
        public UUID uuid = VERIFIED;
    }
}
