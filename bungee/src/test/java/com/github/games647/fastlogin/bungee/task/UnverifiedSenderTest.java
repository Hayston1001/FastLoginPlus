/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.task;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.listener.ConnectListener;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UnverifiedSenderTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void earlyInjectionFailureRemainsVisibleAndLateSenderStillRuns() {
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        FastLoginCore core = mock(FastLoginCore.class);
        Configuration config = mock(Configuration.class);
        doReturn(plugin).when(core).getPlugin();
        when(core.getConfig()).thenReturn(config);
        when(core.getStorage()).thenReturn(mock(SQLStorage.class));
        when(plugin.getCore()).thenReturn(core);
        org.slf4j.Logger log = mock(org.slf4j.Logger.class);
        when(plugin.getLog()).thenReturn(log);
        when(config.getBoolean("autoLogin")).thenReturn(true);
        ProxiedPlayer player = mock(ProxiedPlayer.class, RETURNS_DEEP_STUBS);
        UUID uuid = UUID.randomUUID();
        when(player.getName()).thenReturn("TestUser");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isConnected()).thenReturn(true);
        Server server = mock(Server.class);
        BungeeLoginSession session = new BungeeLoginSession("TestUser", true,
                new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1"));
        session.setIdentityDecision(IdentityClaim.Decision.OFFLINE);
        PendingConnection connection = mock(PendingConnection.class);
        when(connection.getName()).thenReturn("TestUser");
        when(connection.getUniqueId()).thenReturn(uuid);
        when(player.getPendingConnection()).thenReturn(connection);
        java.util.concurrent.ConcurrentMap<PendingConnection, BungeeLoginSession> sessions =
                new java.util.concurrent.ConcurrentHashMap<>();
        sessions.put(connection, session);
        when(plugin.getSession()).thenReturn(sessions);
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(scheduler).runAsync(any(Runnable.class));
        LoginEvent login = mock(LoginEvent.class);
        when(login.getConnection()).thenReturn(connection);
        ServerConnectedEvent connected = mock(ServerConnectedEvent.class);
        when(connected.getPlayer()).thenReturn(player);
        when(connected.getServer()).thenReturn(server);
        ProxyServer proxy = mock(ProxyServer.class, RETURNS_DEEP_STUBS);
        when(proxy.getConfig().getUuid()).thenReturn(UUID.randomUUID().toString());
        try (MockedStatic<ProxyServer> globals = mockStatic(ProxyServer.class)) {
            globals.when(ProxyServer::getInstance).thenReturn(proxy);
            ConnectListener listener = new ConnectListener(plugin, null);
            listener.onLogin(login);
            listener.onLogin(login);
            listener.onServerConnected(connected);
        }
        verify(log, times(1)).warn(startsWith("First early unverified declaration injection failure."));
        verify(login, never()).setCancelled(true);
        assertSame(session, sessions.get(connection));
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
