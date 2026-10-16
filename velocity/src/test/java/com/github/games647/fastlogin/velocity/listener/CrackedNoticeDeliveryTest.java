/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.CrackedNoticeWire;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class CrackedNoticeDeliveryTest {
    private final UUID proxyId = UUID.randomUUID();
    private final UUID playerId = UUID.randomUUID();
    private final AtomicBoolean owed = new AtomicBoolean(true);
    private FastLoginVelocity plugin;
    private FastLoginCore core;
    private SQLStorage storage;
    private StoredProfile record;
    private Player player;
    private ServerConnection connection;
    private RegisteredServer target;
    private CrackedNoticeDelivery delivery;
    private MinecraftChannelIdentifier channel;

    @BeforeEach
    void setUp() {
        plugin = mock(FastLoginVelocity.class);
        core = mock(FastLoginCore.class);
        storage = mock(SQLStorage.class);
        record = mock(StoredProfile.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getName()).thenReturn("fastloginplus");
        when(plugin.getProxyId()).thenReturn(proxyId);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(core.getStorage()).thenReturn(storage);
        Configuration config = new Configuration();
        config.set("verify-backend-messages", true);
        when(core.getConfig()).thenReturn(config);
        when(core.hasSameNameCrackedNotice("TestUser")).thenAnswer(invocation -> owed.get());
        when(core.consumeSameNameCrackedNotice("TestUser")).thenAnswer(invocation -> owed.getAndSet(false));
        when(storage.loadProfile("TestUser")).thenReturn(record);
        when(record.isSameNameCracked()).thenReturn(true);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(1)).run(); return null; })
                .when(storage).withNameLock(eq("TestUser"), any(Runnable.class));
        player = mock(Player.class);
        target = mock(RegisteredServer.class);
        connection = mock(ServerConnection.class);
        when(player.getUsername()).thenReturn("TestUser");
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isActive()).thenReturn(true);
        when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        when(connection.getPlayer()).thenReturn(player);
        when(connection.getServer()).thenReturn(target);
        when(connection.sendPluginMessage(any(), any(byte[].class))).thenReturn(true);
        channel = MinecraftChannelIdentifier.create("fastloginplus", CrackedNoticeWire.CHANNEL);
        delivery = new CrackedNoticeDelivery(plugin);
    }

    private CrackedNoticeWire capability() {
        return new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE, "TestUser", playerId,
                proxyId.toString(), UUID.randomUUID());
    }

    private PluginMessageEvent event(CrackedNoticeWire notice) {
        PluginMessageEvent event = mock(PluginMessageEvent.class);
        when(event.getIdentifier()).thenReturn(channel);
        when(event.getResult()).thenReturn(PluginMessageEvent.ForwardResult.forward());
        when(event.getSource()).thenReturn(connection);
        when(event.getTarget()).thenReturn(player);
        when(event.getData()).thenReturn(notice.encode());
        return event;
    }

    @Test
    void backendAnnouncesFirstAndOnlyTheFirstConfirmationIsHandedOff() throws Exception {
        CrackedNoticeWire capability = capability();
        // Configuration belongs to the incoming ServerConnection before it becomes current.
        when(player.getCurrentServer()).thenReturn(Optional.empty());
        PluginMessageEvent event = event(capability);
        delivery.onPluginMessage(event);
        verify(event).setResult(PluginMessageEvent.ForwardResult.handled());
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        verify(connection, never()).sendPluginMessage(any(), any(byte[].class));
        when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        delivery.deliver(player, target);
        delivery.deliver(player, target);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(connection).sendPluginMessage(eq(channel), bytes.capture());
        CrackedNoticeWire handoff = CrackedNoticeWire.decode(bytes.getValue());
        assertEquals(CrackedNoticeWire.Kind.DELIVER, handoff.kind());
        assertTrue(capability.sameDelivery(handoff));
        verify(core, never()).sendLocaleMessage(anyString(), any());
        assertFalse(owed.get());
    }

    @Test
    void oldBackendKeepsProxyChatAndLateCapabilityCannotDuplicateIt() {
        delivery.deliver(player, target);
        delivery.onPluginMessage(event(capability()));
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(connection, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void clientSourceWrongNameUuidOrUntrustedEchoCannotClaimCapability() {
        PluginMessageEvent client = event(capability());
        when(client.getSource()).thenReturn(player);
        delivery.onPluginMessage(client);
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE, "OtherUser",
                playerId, proxyId.toString(), UUID.randomUUID())));
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE, "TestUser",
                UUID.randomUUID(), proxyId.toString(), UUID.randomUUID())));
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE, "TestUser",
                playerId, UUID.randomUUID().toString(), UUID.randomUUID())));
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(connection, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void duplicateCapabilityStillOnlyHandsOffOnce() {
        delivery.onPluginMessage(event(capability()));
        delivery.onPluginMessage(event(capability()));
        delivery.deliver(player, target);
        delivery.deliver(player, target);
        verify(connection).sendPluginMessage(eq(channel), any(byte[].class));
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void concurrentJoinTasksCannotConsumeOneConfirmationTwice() {
        delivery.onPluginMessage(event(capability()));
        java.util.stream.IntStream.range(0, 16).parallel().forEach(index -> delivery.deliver(player, target));
        verify(connection).sendPluginMessage(eq(channel), any(byte[].class));
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void legacyGenericDispatcherIgnoresTheNewCapabilityAndLeavesProxyChatOwnershipIntact() {
        com.github.games647.fastlogin.core.scheduler.AsyncScheduler scheduler =
                mock(com.github.games647.fastlogin.core.scheduler.AsyncScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(scheduler).runAsync(any(Runnable.class));
        new PluginMessageListener(plugin).onPluginMessage(event(capability()));
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(connection, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void unconfirmedSameNameFactDoesNotInventANotice() {
        owed.set(false);
        delivery.onPluginMessage(event(capability()));
        delivery.deliver(player, target);
        verifyNoInteractions(record);
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        verify(connection, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void disconnectOrServerSwitchDoesNotSpendThePendingNotice() {
        delivery.onPluginMessage(event(capability()));
        when(player.isActive()).thenReturn(false);
        delivery.disconnected(player);
        delivery.deliver(player, target);
        when(player.isActive()).thenReturn(true);
        when(connection.getServer()).thenReturn(mock(RegisteredServer.class));
        delivery.deliver(player, target);
        assertTrue(owed.get());
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
    }

    @Test
    void clearedRecordSuppressesStaleNoticeAndReadFailurePreservesIt() {
        when(storage.loadProfile("TestUser")).thenReturn(null);
        delivery.deliver(player, target);
        assertTrue(owed.get());
        when(storage.loadProfile("TestUser")).thenReturn(record);
        when(record.isSameNameCracked()).thenReturn(false);
        delivery.deliver(player, target);
        assertFalse(owed.get());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void knownUnsentHandoffFallsBackButAnUncertainSendIsNotRetried() {
        delivery.onPluginMessage(event(capability()));
        when(connection.sendPluginMessage(any(), any(byte[].class))).thenReturn(false);
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        owed.set(true); // A later independent first confirmation.
        when(connection.sendPluginMessage(any(), any(byte[].class))).thenThrow(new IllegalStateException("closed"));
        delivery.deliver(player, target);
        verify(core, times(1)).sendLocaleMessage("same-name-cracked-recorded", player);
        assertFalse(owed.get());
    }
}
