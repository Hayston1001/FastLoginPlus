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
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PendingVerificationDisconnectTest {
    @Test
    void bindsOriginalInboundConnectionAndDisconnectUsesLivenessNotNameOnlyCancellation() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getSession()).thenReturn(new ConcurrentHashMap<>());
        when(plugin.getCrackedNoticeDelivery()).thenReturn(mock(CrackedNoticeDelivery.class));
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        InboundConnection inbound = mock(InboundConnection.class);
        when(inbound.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(inbound.isActive()).thenReturn(true);
        PreLoginEvent event = new PreLoginEvent(inbound, "Steve");
        new AsyncPremiumCheck(plugin, inbound, "Steve", event).requestPremiumLogin(
                new VelocityLoginSource(inbound, event), mock(StoredProfile.class), "Steve", true);
        ArgumentCaptor<BooleanSupplier> liveness = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(core).markPendingVerification(eq("Steve"), eq(inbound), liveness.capture());
        assertTrue(liveness.getValue().getAsBoolean());
        when(inbound.isActive()).thenReturn(false);
        assertFalse(liveness.getValue().getAsBoolean(), "works before Velocity has a Player/DisconnectEvent");
        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn("Steve");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(core.getPendingConfirms()).thenReturn(ConcurrentHashMap.newKeySet());
        DisconnectEvent disconnect = mock(DisconnectEvent.class);
        when(disconnect.getPlayer()).thenReturn(player);
        new ConnectListener(plugin, null).onDisconnect(disconnect);
        verify(core).dropDisconnectedVerification("Steve");
        verify(core, never()).dropPendingVerification(anyString());
    }
}
