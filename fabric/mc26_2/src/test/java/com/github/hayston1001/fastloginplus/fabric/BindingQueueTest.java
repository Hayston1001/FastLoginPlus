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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class BindingQueueTest {
    private static final String NAME = "TestUser";
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void passwordHolderQueuesConsentUsingOnlyStartupTrustSettings() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyAuth344Bridge bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        var pending = new ConcurrentHashMap<String, EasyAuth344Bridge.PendingBinding>();
        set(bridge, "easyAuth", EasyAuth.class);
        set(bridge, "entryType", Entry.class);
        set(bridge, "pendingBindings", pending);
        PremiumNameResolver resolver = mock(PremiumNameResolver.class);
        when(resolver.resolve(NAME)).thenReturn(Optional.of(TARGET));
        set(bridge, "premiumNames", resolver);

        ServerPlayer player = mock(ServerPlayer.class, withSettings().extraInterfaces(PlayerAuth.class));
        when(player.getGameProfile()).thenReturn(new GameProfile(OFFLINE, NAME));
        when(((PlayerAuth) player).easyAuth$isAuthenticated()).thenReturn(true);
        when(((PlayerAuth) player).easyAuth$getPlayerEntryV1()).thenReturn(EasyAuth.DB.entry);
        player.connection = mock(ServerGamePacketListenerImpl.class);
        when(player.connection.isAcceptingMessages()).thenReturn(true);
        MinecraftServer server = mock(MinecraftServer.class);
        CompletableFuture<Runnable> completion = new CompletableFuture<>();
        doAnswer(call -> {
            completion.complete(call.getArgument(0));
            return null;
        }).when(server).execute(any(Runnable.class));

        String proxyId = UUID.randomUUID().toString();
        var startup = new FabricConfig.StartupSettings(proxyId);
        // No command-time configuration or client reconnect address is supplied.
        try (var config = mockStatic(FabricConfig.class);
             var consent = mockStatic(BindingConsentWriter.class);
             var messages = mockStatic(FabricMessages.class)) {
            config.when(FabricConfig::startup).thenReturn(startup);
            consent.when(() -> BindingConsentWriter.send(player, NAME, proxyId)).thenAnswer(call -> {
                assertTrue(pending.get(NAME).matchesAccount(OFFLINE, "old-hash"));
                assertTrue(pending.get(NAME).matchesTarget(TARGET));
                return null;
            });
            IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.OFFLINE, NAME, OFFLINE, null,
                    new IdentityClaim.Facts(false, false, IdentityClaim.Consent.NO, true, false, false));

            bridge.beginBinding(server, player, claim);
            completion.get(5, TimeUnit.SECONDS).run();

            consent.verify(() -> BindingConsentWriter.send(player, NAME, proxyId));
            consent.verifyNoMoreInteractions();
            messages.verify(() -> FabricMessages.text("binding-queued", "{uuid}", TARGET.toString()));
            assertEquals(AccountState.BIND_PENDING, bridge.accountStatus(NAME).state());
            assertTrue(pending.get(NAME).matchesTarget(TARGET));
            assertTrue(pending.get(NAME).matchesAccount(OFFLINE, "old-hash"));
            assertEquals("old-hash", EasyAuth.DB.entry.password);
            assertEquals(OFFLINE, EasyAuth.DB.entry.uuid);
            assertEquals("FALSE", EasyAuth.DB.entry.onlineAccount);
            config.verify(FabricConfig::startup);
            config.verify(() -> FabricConfig.warnIfUntrusted(startup));
            config.verifyNoMoreInteractions();
            verify(server).execute(any(Runnable.class));
            verifyNoMoreInteractions(server);
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
        public static final Database DB = new Database();
    }

    public static final class Database {
        final Entry entry = new Entry();

        public Entry getUserData(String name) {
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
