/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.BungeeLoginSource;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.task.AsyncPremiumCheck;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PendingVerificationDisconnectTest {
    @Test
    void bindsPendingConnectionAndDisconnectLeavesItForSettlement() {
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getSession()).thenReturn(new ConcurrentHashMap<>());
        ProxyServer proxy = mock(ProxyServer.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getScheduler()).thenReturn(mock(TaskScheduler.class));
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(plugin.getCrackedNoticeDelivery()).thenReturn(mock(CrackedNoticeDelivery.class));
        PendingConnection connection = mock(PendingConnection.class);
        when(connection.getName()).thenReturn("Steve");
        PreLoginEvent event = mock(PreLoginEvent.class);
        new AsyncPremiumCheck(plugin, event, connection, "Steve").requestPremiumLogin(
                new BungeeLoginSource(connection, event), mock(StoredProfile.class), "Steve", true);
        verify(core).markPendingVerification("Steve", connection);

        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("Steve");
        when(player.getPendingConnection()).thenReturn(connection);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(core.getPendingConfirms()).thenReturn(ConcurrentHashMap.newKeySet());
        new ConnectListener(plugin, null).onDisconnect(new PlayerDisconnectEvent(player));

        // the attempt stays pending: the proxy's own rejection is only visible
        // as this disconnect, so the core settles it instead of dropping it
        verify(core, never()).dropPendingVerification(anyString());
    }
}
