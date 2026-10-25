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
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PendingVerificationDisconnectTest {
    @Test
    void bindsPendingConnectionBeforePlayerExistsAndDisconnectDropsOnlyThatOwner() {
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getSession()).thenReturn(new ConcurrentHashMap<>());
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        PendingConnection connection = mock(PendingConnection.class);
        when(connection.getName()).thenReturn("Steve");
        when(connection.isConnected()).thenReturn(true);
        PreLoginEvent event = mock(PreLoginEvent.class);
        new AsyncPremiumCheck(plugin, event, connection, "Steve").requestPremiumLogin(
                new BungeeLoginSource(connection, event), mock(StoredProfile.class), "Steve", true);
        ArgumentCaptor<BooleanSupplier> liveness = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(core).markPendingVerification(eq("Steve"), eq(connection), liveness.capture());
        assertTrue(liveness.getValue().getAsBoolean());
        when(connection.isConnected()).thenReturn(false);
        assertFalse(liveness.getValue().getAsBoolean(), "works before PlayerDisconnectEvent can fire");
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("Steve");
        when(player.getPendingConnection()).thenReturn(connection);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(core.getPendingConfirms()).thenReturn(ConcurrentHashMap.newKeySet());
        new ConnectListener(plugin, null).onDisconnect(new PlayerDisconnectEvent(player));
        verify(core).dropPendingVerification("Steve", connection);
        verify(core, never()).dropPendingVerification(anyString());
    }
}
