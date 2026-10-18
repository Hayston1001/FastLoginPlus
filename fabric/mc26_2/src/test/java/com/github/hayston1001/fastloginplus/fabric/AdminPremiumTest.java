/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminPremiumTest {
    private static final String NAME = "OtherUser";
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String PROXY = "00000000-0000-0000-0000-000000000003";

    private EasyAuth344Bridge bridge;
    private PremiumNameResolver resolver;
    private MinecraftServer server;
    private ServerPlayer carrier;
    private ConcurrentHashMap<String, EasyAuth344Bridge.PendingBinding> pending;
    private final List<Component> feedback = new ArrayList<>();
    private final CompletableFuture<Runnable> completion = new CompletableFuture<>();

    @BeforeEach
    void setUp() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyAuth.DB = new Database();
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        pending = new ConcurrentHashMap<>();
        set("easyAuth", EasyAuth.class);
        set("entryType", Entry.class);
        set("pendingBindings", pending);
        resolver = mock(PremiumNameResolver.class);
        doAnswer(call -> {
            assertNotSame(EasyAuth.DB.serverThread, Thread.currentThread());
            return Optional.of(TARGET);
        }).when(resolver).resolve(NAME);
        set("premiumNames", resolver);
        server = mock(MinecraftServer.class);
        // Capture the real async completion and drain it inline on the test's
        // server thread, preserving thread-local networking mocks and DB ownership.
        doAnswer(call -> {
            completion.complete(call.getArgument(0));
            return null;
        }).when(server).execute(any(Runnable.class));
        carrier = mock(ServerPlayer.class);
        carrier.connection = mock(ServerGamePacketListenerImpl.class);
        when(carrier.connection.isAcceptingMessages()).thenReturn(true);
    }

    @Test
    void boundAccountRefusesWithoutConsentOrQueue() {
        EasyAuth.DB.entry.password = "";
        EasyAuth.DB.entry.onlineAccount = "TRUE";
        EasyAuth.DB.entry.uuid = TARGET;
        try (var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertFeedback("admin-premium-already", "{player}", NAME);
            consent.verifyNoInteractions();
            verifyNoInteractions(resolver, carrier, server);
            assertTrue(pending.isEmpty());
        }
    }

    @Test
    void conflictingAccountRefusesWithoutConsentOrQueue() {
        EasyAuth.DB.entry.uuid = TARGET;
        try (var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertFeedback("ownership-conflict");
            consent.verifyNoInteractions();
            verifyNoInteractions(resolver, server);
            assertTrue(pending.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void openNameSendsOnlyConsent(boolean existingUnclaimedRow) {
        if (existingUnclaimedRow) {
            EasyAuth.DB.entry.password = "";
            EasyAuth.DB.entry.onlineAccount = "UNKNOWN";
        } else {
            EasyAuth.DB.entry = null;
        }
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertFeedback("admin-premium-open", "{player}", NAME);
            consent.verify(() -> BindingConsentWriter.sendForOther(carrier, NAME, PROXY));
            consent.verifyNoMoreInteractions();
            verifyNoInteractions(resolver, server);
            assertTrue(pending.isEmpty());
            assertStartupOnly(config);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void passwordAccountQueuesAndExistingWindowIsReplaced(boolean existingWindow) throws Exception {
        if (existingWindow) {
            pending.put(NAME, new EasyAuth344Bridge.PendingBinding(OFFLINE, "old-hash", UUID.randomUUID(),
                    System::nanoTime));
        }
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            consent.when(() -> BindingConsentWriter.sendForOther(carrier, NAME, PROXY)).thenAnswer(call -> {
                assertTrue(pending.get(NAME).matchesAccount(OFFLINE, "old-hash"));
                assertTrue(pending.get(NAME).matchesTarget(TARGET));
                return null;
            });
            request();
            finish();
            assertFeedback("admin-premium-queued", "{player}", NAME, "{uuid}", TARGET.toString());
            assertEquals(AccountState.BIND_PENDING, bridge.accountStatus(NAME).state());
            assertEquals("old-hash", EasyAuth.DB.entry.password);
            assertEquals(OFFLINE, EasyAuth.DB.entry.uuid);
            assertEquals("FALSE", EasyAuth.DB.entry.onlineAccount);
            consent.verify(() -> BindingConsentWriter.sendForOther(carrier, NAME, PROXY));
            consent.verifyNoMoreInteractions();
            // No backend kick-toggle or other command-time settings are consulted.
            assertStartupOnly(config);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unresolvedNameDoesNotQueueOrSend(boolean throwsException) throws Exception {
        doAnswer(call -> {
            if (throwsException) {
                throw new IllegalStateException("lookup failed");
            }
            return Optional.empty();
        }).when(resolver).resolve(NAME);
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            finish();
            assertFeedback("admin-premium-name-unavailable", "{player}", NAME);
            assertTrue(pending.isEmpty());
            consent.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "uuid", "kind", "deleted", "name"})
    void accountChangesDuringLookupAreRejected(String change) throws Exception {
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            switch (change) {
                case "password" -> EasyAuth.DB.entry.password = "new-hash";
                case "uuid" -> EasyAuth.DB.entry.uuid = TARGET;
                case "kind" -> EasyAuth.DB.entry.onlineAccount = "TRUE";
                case "deleted" -> EasyAuth.DB.entry = null;
                case "name" -> EasyAuth.DB.entry.username = "DifferentUser";
                default -> throw new AssertionError(change);
            }
            finish();
            assertFeedback("admin-premium-changed", "{player}", NAME);
            assertTrue(pending.isEmpty());
            consent.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readFailureReturnsAuthUnavailable(boolean duringCompletion) throws Exception {
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            if (duringCompletion) {
                request();
                EasyAuth.DB.fail = true;
                finish();
            } else {
                EasyAuth.DB.fail = true;
                request();
            }
            assertFeedback("auth-unavailable");
            assertTrue(pending.isEmpty());
            consent.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedConsentRestoresPreviousQueue(boolean existingWindow) throws Exception {
        var previous = new EasyAuth344Bridge.PendingBinding(OFFLINE, "old-hash", UUID.randomUUID(),
                System::nanoTime);
        if (existingWindow) {
            pending.put(NAME, previous);
        }
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            consent.when(() -> BindingConsentWriter.sendForOther(carrier, NAME, PROXY))
                    .thenThrow(new IllegalStateException("send failed"));
            request();
            finish();
            assertFeedback("auth-unavailable");
            if (existingWindow) {
                assertSame(previous, pending.get(NAME));
            } else {
                assertTrue(pending.isEmpty());
            }
            assertEquals("old-hash", EasyAuth.DB.entry.password);
        }
    }

    @Test
    void disconnectedCarrierDoesNotLeaveANewQueue() throws Exception {
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            when(carrier.connection.isAcceptingMessages()).thenReturn(false);
            finish();
            assertFeedback("auth-unavailable");
            assertTrue(pending.isEmpty());
            consent.verifyNoInteractions();
        }
    }

    private void request() {
        bridge.adminPremium(server, carrier, NAME, "Admin", message -> {
            assertSame(EasyAuth.DB.serverThread, Thread.currentThread());
            feedback.add(message);
        });
    }

    private void finish() throws Exception {
        Runnable task = completion.get(5, TimeUnit.SECONDS);
        assertTrue(feedback.isEmpty());
        task.run();
        assertFalse(feedback.isEmpty());
    }

    private void assertFeedback(String key, String... replacements) {
        assertEquals(List.of(FabricMessages.text(key, replacements).getString()),
                feedback.stream().map(Component::getString).toList());
    }

    private MockedStatic<FabricConfig> config() {
        var config = mockStatic(FabricConfig.class);
        config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY));
        return config;
    }

    private void assertStartupOnly(MockedStatic<FabricConfig> config) {
        config.verify(FabricConfig::startup);
        config.verify(() -> FabricConfig.warnIfUntrusted(new FabricConfig.StartupSettings(PROXY)));
        config.verifyNoMoreInteractions();
    }

    private void set(String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    public static final class EasyAuth {
        public static Database DB;
    }

    public static final class Database {
        final Thread serverThread = Thread.currentThread();
        Entry entry = new Entry();
        boolean fail;

        public Entry getUserData(String name) {
            assertSame(serverThread, Thread.currentThread());
            assertEquals(NAME, name);
            if (fail) {
                throw new IllegalStateException("database unavailable");
            }
            return entry;
        }
    }

    public static final class Entry {
        public String username = NAME;
        public String password = "old-hash";
        public UUID uuid = OFFLINE;
        public String onlineAccount = "FALSE";
    }
}
