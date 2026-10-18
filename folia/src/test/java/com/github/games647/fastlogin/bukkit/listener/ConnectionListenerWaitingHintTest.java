/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.BungeeManager;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class ConnectionListenerWaitingHintTest {

    private final FastLoginBukkit plugin = mock(FastLoginBukkit.class);
    private final FastLoginCore core = mock(FastLoginCore.class);
    private final BungeeManager manager = mock(BungeeManager.class);
    private final Player player = mock(Player.class);
    private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
    private final BukkitLoginSession session = new BukkitLoginSession("TestUser", false);
    private final ConnectionListener listener = new ConnectionListener(plugin);
    private final PlayerJoinEvent event = mock(PlayerJoinEvent.class);

    ConnectionListenerWaitingHintTest() {
        doReturn(core).when(plugin).getCore();
        when(plugin.getBungeeManager()).thenReturn(manager);
        when(manager.isEnabled()).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("TestUser");
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(spigot.getRawAddress()).thenReturn(address);
        when(plugin.getSession(address)).thenReturn(session);
    }

    @Test
    void standaloneDoesNotInspectSessionOrSendHint() {
        when(manager.isEnabled()).thenReturn(false);
        listener.onPlayerJoinWaitingHint(event);
        verify(plugin, never()).getSession(any());
        verifyNoInteractions(core);
    }

    @Test
    void missingSessionDoesNotSendHint() {
        when(plugin.getSession(address)).thenReturn(null);
        listener.onPlayerJoinWaitingHint(event);
        verifyNoInteractions(core);
    }

    @Test
    void earlyFactsDoNotSendHint() {
        session.setFacts(facts());
        listener.onPlayerJoinWaitingHint(event);
        verifyNoInteractions(core);
    }

    @Test
    void proxySessionWithoutFactsGetsExactlyOneHintUsingRawAddress() {
        listener.onPlayerJoinWaitingHint(event);
        verify(plugin).getSession(address);
        verify(player, never()).getAddress();
        verify(player, never()).getScheduler();
        verify(core).sendLocaleMessage("account-state-checking", player);
        verifyNoMoreInteractions(core);
        assertFalse(session.isFactsPresent());
    }

    @Test
    void lateFactsAndOriginalDelayedLoginDoNotResendHint() {
        EntityScheduler scheduler = mock(EntityScheduler.class);
        AsyncScheduler async = mock(AsyncScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(async);
            listener.onPlayerJoinWaitingHint(event);
            session.setFacts(facts());
            when(player.isOnline()).thenReturn(true);
            when(plugin.deliverPendingLogin(player)).thenReturn(true);
            listener.onPlayerJoin(event);
            ArgumentCaptor<Consumer<ScheduledTask>> delayed = ArgumentCaptor.forClass(Consumer.class);
            verify(scheduler).runDelayed(eq(plugin), delayed.capture(), isNull(), eq(10L));
            delayed.getValue().accept(mock(ScheduledTask.class));
            verify(async).runNow(eq(plugin), any());
            verify(manager).markJoinEventFired(player);
            verify(core).sendLocaleMessage("account-state-checking", player);
            verifyNoMoreInteractions(core);
            assertTrue(session.isFactsPresent());
        }
    }

    @Test
    void originalDelayedLoginStillStopsForDisconnectedPlayer() {
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            listener.onPlayerJoinWaitingHint(event);
            listener.onPlayerJoin(event);
            ArgumentCaptor<Consumer<ScheduledTask>> delayed = ArgumentCaptor.forClass(Consumer.class);
            verify(scheduler).runDelayed(eq(plugin), delayed.capture(), isNull(), eq(10L));
            delayed.getValue().accept(mock(ScheduledTask.class));
            verify(plugin, never()).deliverPendingLogin(any());
            bukkit.verify(Bukkit::getAsyncScheduler, never());
            verify(manager, never()).markJoinEventFired(any());
            verify(core).sendLocaleMessage("account-state-checking", player);
            verifyNoMoreInteractions(core);
            assertFalse(session.isFactsPresent());
        }
    }

    @Test
    void waitingHintIsIndependentLowestHandlerAndOriginalJoinRemainsNormal() throws Exception {
        EventHandler hint = ConnectionListener.class
                .getMethod("onPlayerJoinWaitingHint", PlayerJoinEvent.class).getAnnotation(EventHandler.class);
        assertNotNull(hint);
        assertEquals(EventPriority.LOWEST, hint.priority());
        assertTrue(hint.ignoreCancelled());
        EventHandler original = ConnectionListener.class
                .getMethod("onPlayerJoin", PlayerJoinEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.NORMAL, original.priority());
        assertTrue(original.ignoreCancelled());
    }

    @Test
    void bothClassicLanguagesSupplyOneLineHint() throws Exception {
        for (String language : new String[]{"en", "zh"}) {
            try (InputStream input = getClass().getResourceAsStream("/messages_" + language + ".yml")) {
                assertNotNull(input);
                String message = ConfigurationProvider.getProvider(YamlConfiguration.class)
                        .load(input).getString("account-state-checking");
                assertFalse(message.isEmpty());
                assertFalse(message.contains("\n"));
            }
        }
    }

    private static IdentityClaim.Facts facts() {
        return new IdentityClaim.Facts(false, false, IdentityClaim.Consent.UNKNOWN, true, true, false);
    }
}
