/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.event.BungeeFastLoginAntiBotEvent;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectListenerAntiBotTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void blockDeniesUnlessTheAntiBotEventIsCancelled(boolean cancelled) {
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(manager);
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        FastLoginCore<?, ?, ?> core = mock(FastLoginCore.class);
        doReturn(core).when(plugin).getCore();
        when(core.getMessage("kick-antibot")).thenReturn("Blocked");
        PendingConnection connection = mock(PendingConnection.class);
        InetSocketAddress address = new InetSocketAddress("1.2.3.4", 25565);
        when(connection.getAddress()).thenReturn(address);
        when(connection.getName()).thenReturn("Steve");
        PreLoginEvent event = mock(PreLoginEvent.class);
        when(event.getConnection()).thenReturn(connection);
        AntiBotService antiBot = mock(AntiBotService.class);
        when(antiBot.onIncomingConnection(address, "Steve")).thenReturn(Action.Block);
        when(manager.callEvent(any(BungeeFastLoginAntiBotEvent.class))).thenAnswer(invocation -> {
            BungeeFastLoginAntiBotEvent fired = invocation.getArgument(0);
            assertSame(Action.Block, fired.getAction());
            fired.setCancelled(cancelled);
            return fired;
        });

        new ConnectListener(plugin, antiBot).onPreLogin(event);

        verify(manager, times(1)).callEvent(any(BungeeFastLoginAntiBotEvent.class));
        if (cancelled) {
            verify(event, never()).setCancelled(true);
            verify(event, times(1)).registerIntent(plugin);
            verify(scheduler, times(1)).runAsync(any(Runnable.class));
        } else {
            verify(event, times(1)).setCancelled(true);
            verify(event, never()).registerIntent(plugin);
            verify(scheduler, never()).runAsync(any(Runnable.class));
        }
    }
}
