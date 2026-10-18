/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.CrackedNoticeClaim;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.GameProfile;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class NoticeProfileTest {
    @org.junit.jupiter.api.Test
    void verifiedJoinNeverSchedulesStaleCrackedNotificationDebt() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        com.github.games647.fastlogin.core.scheduler.AsyncScheduler scheduler =
                mock(com.github.games647.fastlogin.core.scheduler.AsyncScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        com.velocitypowered.api.proxy.Player player = mock(com.velocitypowered.api.proxy.Player.class);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        when(player.getRemoteAddress()).thenReturn(address);
        when(player.isOnlineMode()).thenReturn(true);
        VelocityLoginSession session = new VelocityLoginSession("TestUser", true,
                new StoredProfile(UUID.randomUUID(), "TestUser", false, FloodgateState.FALSE, "127.0.0.1"));
        session.setIdentityDecision(IdentityClaim.Decision.PREMIUM_VERIFIED);
        ConcurrentHashMap<InetSocketAddress, VelocityLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put(address, session);
        when(plugin.getSession()).thenReturn(sessions);
        com.velocitypowered.api.event.player.ServerConnectedEvent event =
                mock(com.velocitypowered.api.event.player.ServerConnectedEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getServer()).thenReturn(mock(com.velocitypowered.api.proxy.server.RegisteredServer.class));
        new ConnectListener(plugin, null).onServerConnected(event);
        verify(scheduler, never()).runAsync(org.mockito.ArgumentMatchers.any());
        verify(plugin, never()).getCrackedNoticeDelivery();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    void onlyRealPendingDebtAddsPropertyAndIncomingReservedPropertiesAreStripped(boolean owed, boolean failure) {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getName()).thenReturn("fastloginplus");
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(core.hasSameNameCrackedNotice("TestUser")).thenReturn(owed);
        Configuration config = new Configuration();
        config.set("secondAttemptCracked", true);
        config.set("autoLogin", false);
        config.set("premiumUuid", false);
        when(core.getConfig()).thenReturn(config);
        CrackedNoticeDelivery delivery = spy(new CrackedNoticeDelivery(plugin));
        when(plugin.getCrackedNoticeDelivery()).thenReturn(delivery);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        UUID id = UUID.nameUUIDFromBytes("OfflinePlayer:TestUser".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StoredProfile record = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        record.setSameNameCracked(true);
        VelocityLoginSession session = new VelocityLoginSession("TestUser", true, record);
        session.setIdentityDecision(IdentityClaim.Decision.OFFLINE);
        ConcurrentHashMap<InetSocketAddress, VelocityLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put(address, session);
        when(plugin.getSession()).thenReturn(sessions);
        InboundConnection connection = mock(InboundConnection.class);
        when(connection.getRemoteAddress()).thenReturn(address);
        if (failure) {
            doThrow(new IllegalStateException("optional notification failed")).when(delivery)
                    .prepare(connection, "TestUser", id);
        }
        GameProfileRequestEvent event = mock(GameProfileRequestEvent.class);
        when(event.getConnection()).thenReturn(connection);
        when(event.getGameProfile()).thenReturn(new GameProfile(id, "TestUser", Arrays.asList(
                new GameProfile.Property(CrackedNoticeClaim.PROPERTY_NAME, "forged", ""),
                new GameProfile.Property(IdentityClaim.PROPERTY_NAME, "forged", ""),
                new GameProfile.Property("textures", "skin", ""))));
        new ConnectListener(plugin, null).onGameProfileRequest(event);
        ArgumentCaptor<GameProfile> captured = ArgumentCaptor.forClass(GameProfile.class);
        verify(event).setGameProfile(captured.capture());
        List<GameProfile.Property> properties = captured.getValue().getProperties();
        IdentityClaim identity = IdentityClaim.parseExactlyOne(properties.stream()
                .filter(value -> IdentityClaim.PROPERTY_NAME.equals(value.getName())).map(GameProfile.Property::getValue)
                .collect(Collectors.toList()), "TestUser", id);
        assertFalse(identity.isAutoLogin());
        assertEquals(IdentityClaim.Decision.OFFLINE, identity.getDecision());
        assertEquals(12, identity.encode().split(":").length);
        CrackedNoticeClaim notice = CrackedNoticeClaim.parse(properties.stream()
                .filter(value -> CrackedNoticeClaim.PROPERTY_NAME.equals(value.getName()))
                .map(GameProfile.Property::getValue).collect(Collectors.toList()), "TestUser", id);
        assertEquals(owed && !failure, notice != null);
        if (notice != null) { assertTrue(notice.accepts(identity)); }
        assertTrue(properties.stream().anyMatch(value -> "textures".equals(value.getName())));
        verify(core, never()).consumeSameNameCrackedNotice("TestUser");
    }
}
