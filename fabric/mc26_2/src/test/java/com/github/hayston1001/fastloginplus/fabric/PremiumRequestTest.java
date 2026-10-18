/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class PremiumRequestTest {
    private static final String NAME = "TestUser";
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private EasyAuth344Bridge bridge;
    private ServerPlayer player;

    @BeforeEach
    void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyAuth.DB = new Database();
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        set(bridge, "easyAuth", EasyAuth.class);
        set(bridge, "entryType", Entry.class);

        player = mock(ServerPlayer.class, withSettings().extraInterfaces(PlayerAuth.class));
        when(player.getGameProfile()).thenReturn(new GameProfile(TARGET, NAME));
        when(((PlayerAuth) player).easyAuth$isAuthenticated()).thenReturn(true);
        when(((PlayerAuth) player).easyAuth$getPlayerEntryV1()).thenReturn(EasyAuth.DB.entry);
        player.connection = mock(ServerGamePacketListenerImpl.class);
        when(player.connection.isAcceptingMessages()).thenReturn(true);
    }

    @ParameterizedTest
    @EnumSource(value = IdentityClaim.Decision.class, names = {"OFFLINE", "PREMIUM_VERIFIED"})
    void passwordHolderRequestsOnlyConsentUsingStartupTrustSettings(IdentityClaim.Decision decision) {
        boolean verified = decision == IdentityClaim.Decision.PREMIUM_VERIFIED;
        UUID forwarded = verified ? TARGET : OFFLINE;
        when(player.getGameProfile()).thenReturn(new GameProfile(forwarded, NAME));

        String proxyId = UUID.randomUUID().toString();
        var startup = new FabricConfig.StartupSettings(proxyId);
        // No command-time configuration or client reconnect address is supplied.
        try (var config = mockStatic(FabricConfig.class);
             var consent = mockStatic(BindingConsentWriter.class);
             var messages = mockStatic(FabricMessages.class)) {
            config.when(FabricConfig::startup).thenReturn(startup);
            IdentityClaim claim = new IdentityClaim(decision, NAME, forwarded, verified ? TARGET : null,
                    new IdentityClaim.Facts(false, false, IdentityClaim.Consent.NO, true, false, false));

            bridge.beginBinding(player, claim);
            bridge.beginBinding(player, claim);

            consent.verify(() -> BindingConsentWriter.send(player, NAME, proxyId), org.mockito.Mockito.times(2));
            consent.verifyNoMoreInteractions();
            messages.verify(() -> FabricMessages.text("premium-requested"), org.mockito.Mockito.times(2));
            assertEquals(AccountState.PASSWORD, bridge.accountStatus(NAME).state());
            assertEquals("old-hash", EasyAuth.DB.entry.password);
            assertEquals(OFFLINE, EasyAuth.DB.entry.uuid);
            assertEquals("FALSE", EasyAuth.DB.entry.onlineAccount);
            config.verify(FabricConfig::startup, org.mockito.Mockito.times(2));
            config.verify(() -> FabricConfig.warnIfUntrusted(startup), org.mockito.Mockito.times(2));
            config.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"send", "disconnected", "read", "unauthenticated"})
    void requestFailureNeverChangesThePasswordAccount(String failure) {
        String proxyId = UUID.randomUUID().toString();
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, NAME, TARGET, TARGET);
        try (var config = mockStatic(FabricConfig.class); var consent = mockStatic(BindingConsentWriter.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(proxyId));
            switch (failure) {
                case "send" -> consent.when(() -> BindingConsentWriter.send(player, NAME, proxyId))
                        .thenThrow(new IllegalStateException("send unavailable"));
                case "disconnected" -> when(player.connection.isAcceptingMessages()).thenReturn(false);
                case "read" -> EasyAuth.DB.failRead = true;
                case "unauthenticated" -> when(((PlayerAuth) player).easyAuth$isAuthenticated()).thenReturn(false);
                default -> throw new AssertionError(failure);
            }
            assertThrows(RuntimeException.class, () -> bridge.beginBinding(player, claim));
            assertEquals("old-hash", EasyAuth.DB.entry.password);
            assertEquals(OFFLINE, EasyAuth.DB.entry.uuid);
            assertEquals("FALSE", EasyAuth.DB.entry.onlineAccount);
            if (!"send".equals(failure)) {
                consent.verifyNoInteractions();
            }
        }
    }

    private static void set(EasyAuth344Bridge bridge, String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    public interface PlayerAuth {
        boolean easyAuth$isAuthenticated();

        Entry easyAuth$getPlayerEntryV1();
    }

    public static final class EasyAuth {
        public static Database DB;
    }

    public static final class Database {
        final Entry entry = new Entry();
        boolean failRead;

        public Entry getUserData(String name) {
            if (failRead) {
                throw new IllegalStateException("read unavailable");
            }
            return NAME.equals(name) ? entry : null;
        }
    }

    public static final class Entry {
        public String username = NAME;
        public String password = "old-hash";
        public UUID uuid = OFFLINE;
        public String onlineAccount = "FALSE";
    }
}
