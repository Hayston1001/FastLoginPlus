/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.listener.ConnectListener;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UnverifiedSenderTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void offlineEarlyDeliveryOrVisibleFailureNeverDisablesTheLateSender(boolean injectionFails) {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore core = mock(FastLoginCore.class);
        SQLStorage storage = mock(SQLStorage.class);
        Configuration config = mock(Configuration.class);
        doReturn(plugin).when(core).getPlugin();
        when(core.getConfig()).thenReturn(config);
        when(core.getStorage()).thenReturn(storage);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getProxyId()).thenReturn(UUID.randomUUID());
        org.slf4j.Logger log = mock(org.slf4j.Logger.class);
        when(plugin.getLog()).thenReturn(log);
        when(config.getBoolean("autoLogin")).thenReturn(true);
        when(config.getBoolean("auto-register-unknown")).thenReturn(true);
        when(config.get("premiumUuid")).thenReturn(false);
        Player player = mock(Player.class);
        UUID uuid = com.github.games647.craftapi.UUIDAdapter.generateOfflineId("TestUser");
        when(player.getUsername()).thenReturn("TestUser");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isActive()).thenReturn(true);
        RegisteredServer server = mock(RegisteredServer.class);
        VelocityLoginSession session = new VelocityLoginSession("TestUser", true,
                new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1"));
        session.setIdentityDecision(IdentityClaim.Decision.OFFLINE);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        when(player.getRemoteAddress()).thenReturn(address);
        java.util.concurrent.ConcurrentMap<InetSocketAddress, VelocityLoginSession> sessions =
                new java.util.concurrent.ConcurrentHashMap<>();
        sessions.put(address, session);
        when(plugin.getSession()).thenReturn(sessions);
        InboundConnection connection = mock(InboundConnection.class);
        when(connection.getRemoteAddress()).thenReturn(address);
        GameProfileRequestEvent early = mock(GameProfileRequestEvent.class);
        when(early.getConnection()).thenReturn(connection);
        when(early.getUsername()).thenReturn("TestUser");
        if (injectionFails) {
            when(early.getGameProfile()).thenThrow(new IllegalStateException("injected profile API failure"));
        } else {
            when(early.getGameProfile()).thenReturn(new GameProfile(uuid, "TestUser", Collections.emptyList()));
        }
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(scheduler).runAsyncDelayed(any(Runnable.class), any(Duration.class));
        ServerConnectedEvent connected = mock(ServerConnectedEvent.class);
        when(connected.getPlayer()).thenReturn(player);
        when(connected.getServer()).thenReturn(server);
        ConnectListener listener = new ConnectListener(plugin, null);
        listener.onGameProfileRequest(early);
        if (injectionFails) {
            listener.onGameProfileRequest(early);
            verify(log, times(1)).warn(startsWith("First early unverified declaration injection failure."),
                    any(Throwable.class));
        } else {
            ArgumentCaptor<GameProfile> profile = ArgumentCaptor.forClass(GameProfile.class);
            verify(early).setGameProfile(profile.capture());
            IdentityClaim claim = IdentityClaim.parseExactlyOne(profile.getValue().getProperties().stream()
                    .filter(property -> property.getName().equals(IdentityClaim.PROPERTY_NAME))
                    .map(GameProfile.Property::getValue).collect(java.util.stream.Collectors.toList()),
                    "TestUser", uuid);
            assertEquals(IdentityClaim.Decision.OFFLINE, claim.getDecision());
            assertNull(claim.getVerifiedMojangUuid());
            assertEquals(IdentityClaim.Consent.NO, claim.getConsent());
        }
        listener.onServerConnected(connected);
        ArgumentCaptor<LoginActionMessage> message = ArgumentCaptor.forClass(LoginActionMessage.class);
        verify(plugin).sendPluginMessage(eq(server), message.capture());
        assertEquals(LoginActionMessage.Type.CRACKED, message.getValue().getType());
        assertEquals(IdentityClaim.Decision.OFFLINE, message.getValue().getIdentityDecision());
        assertNull(message.getValue().getVerifiedPremiumUuid());
        assertEquals(uuid, message.getValue().getForwardedUuid());
        assertEquals(IdentityClaim.Consent.NO, message.getValue().getConsent());
        assertTrue(message.getValue().isAutoLogin());
    }
}
