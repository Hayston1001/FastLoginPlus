/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSource;
import com.github.games647.fastlogin.velocity.task.AsyncPremiumCheck;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PendingVerificationDisconnectTest {
    @Test
    void bindsOriginalInboundConnectionAndDisconnectLeavesItForSettlement() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getSession()).thenReturn(new ConcurrentHashMap<>());
        when(plugin.getCrackedNoticeDelivery()).thenReturn(mock(CrackedNoticeDelivery.class));
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        InboundConnection inbound = mock(InboundConnection.class);
        when(inbound.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        PreLoginEvent event = new PreLoginEvent(inbound, "Steve");
        new AsyncPremiumCheck(plugin, inbound, "Steve", event).requestPremiumLogin(
                new VelocityLoginSource(inbound, event), mock(StoredProfile.class), "Steve", true);
        verify(core).markPendingVerification("Steve", inbound);

        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn("Steve");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(core.getPendingConfirms()).thenReturn(ConcurrentHashMap.newKeySet());
        DisconnectEvent disconnect = mock(DisconnectEvent.class);
        when(disconnect.getPlayer()).thenReturn(player);
        new ConnectListener(plugin, null).onDisconnect(disconnect);

        // the attempt stays pending: the proxy's own rejection is only visible
        // as this disconnect, so the core settles it instead of dropping it
        verify(core, never()).dropPendingVerification(anyString());
    }
}
