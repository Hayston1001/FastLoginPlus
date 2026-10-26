/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
    private ServerPlayer carrier;
    private final List<Component> feedback = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyAuth.DB = new Database();
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        set("easyAuth", EasyAuth.class);
        set("entryType", Entry.class);
        carrier = mock(ServerPlayer.class);
        carrier.connection = mock(ServerGamePacketListenerImpl.class);
        when(carrier.connection.isAcceptingMessages()).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "dormant-hash"})
    void boundAccountRefusesWithoutSendingConsent(String password) {
        EasyAuth.DB.entry.password = password;
        EasyAuth.DB.entry.onlineAccount = "TRUE";
        EasyAuth.DB.entry.uuid = TARGET;
        try (var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertFeedback("admin-premium-already", "{player}", NAME);
            consent.verifyNoInteractions();
            verifyNoInteractions(carrier);
        }
    }

    @Test
    void conflictingAccountRefusesWithoutSendingConsent() {
        EasyAuth.DB.entry.uuid = TARGET;
        try (var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertFeedback("ownership-conflict");
            consent.verifyNoInteractions();
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
            assertEquals(List.of(), feedback);
            consent.verify(() -> BindingConsentWriter.sendForOther(carrier, NAME, PROXY));
            consent.verifyNoMoreInteractions();
            assertStartupOnly(config);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"FALSE", "UNKNOWN"})
    void passwordAccountRequestsOnlyConsentSynchronously(String kind) {
        EasyAuth.DB.entry.onlineAccount = kind;
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertEquals(List.of(), feedback);
            assertEquals(AccountState.PASSWORD, bridge.accountStatus(NAME).state());
            assertOriginal(kind);
            consent.verify(() -> BindingConsentWriter.sendForOther(carrier, NAME, PROXY));
            consent.verifyNoMoreInteractions();
            assertStartupOnly(config);
        }
    }

    @Test
    void readFailureReturnsAuthUnavailableWithoutSendingConsent() {
        EasyAuth.DB.fail = true;
        try (var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertFeedback("auth-unavailable");
            consent.verifyNoInteractions();
            assertOriginal("FALSE");
        }
    }

    @Test
    void failedConsentLeavesAccountUnchanged() {
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            consent.when(() -> BindingConsentWriter.sendForOther(carrier, NAME, PROXY))
                    .thenThrow(new IllegalStateException("send failed"));
            request();
            assertFeedback("auth-unavailable");
            assertOriginal("FALSE");
        }
    }

    @Test
    void disconnectedCarrierCannotSendConsentOrModifyAccount() {
        when(carrier.connection.isAcceptingMessages()).thenReturn(false);
        try (var config = config(); var consent = mockStatic(BindingConsentWriter.class)) {
            request();
            assertFeedback("auth-unavailable");
            consent.verifyNoInteractions();
            assertOriginal("FALSE");
        }
    }

    private void assertOriginal(String kind) {
        assertEquals("old-hash", EasyAuth.DB.entry.password);
        assertEquals(OFFLINE, EasyAuth.DB.entry.uuid);
        assertEquals(kind, EasyAuth.DB.entry.onlineAccount);
    }

    private void request() {
        bridge.adminPremium(carrier, NAME, "Admin", message -> {
            assertSame(EasyAuth.DB.serverThread, Thread.currentThread());
            feedback.add(message);
        });
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
