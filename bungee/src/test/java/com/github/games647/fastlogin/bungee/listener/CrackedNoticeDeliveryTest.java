/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.CrackedNoticeClaim;
import com.github.games647.fastlogin.protocol.CrackedNoticeWire;
import com.google.common.base.Ticker;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class CrackedNoticeDeliveryTest {
    private final UUID proxyId = UUID.randomUUID();
    private final UUID playerId = UUID.randomUUID();
    private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
    private final AtomicBoolean owed = new AtomicBoolean(true);
    private FastLoginBungee plugin;
    private FastLoginCore core;
    private SQLStorage storage;
    private StoredProfile record;
    private ProxiedPlayer player;
    private Server connection;
    private ServerInfo serverInfo;
    private CrackedNoticeDelivery delivery;
    private AsyncScheduler scheduler;
    private PendingConnection inbound;
    private Runnable timeout;

    @BeforeEach
    void setUp() {
        plugin = mock(FastLoginBungee.class);
        core = mock(FastLoginCore.class);
        storage = mock(SQLStorage.class);
        record = mock(StoredProfile.class);
        scheduler = mock(AsyncScheduler.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getName()).thenReturn("fastloginplus");
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(scheduler).runAsync(any(Runnable.class));
        doAnswer(invocation -> {
            timeout = invocation.getArgument(0);
            return null;
        }).when(scheduler).runAsyncDelayed(any(Runnable.class), any(Duration.class));
        when(core.getStorage()).thenReturn(storage);
        Configuration config = new Configuration();
        config.set("verify-backend-messages", true);
        when(core.getConfig()).thenReturn(config);
        when(core.hasSameNameCrackedNotice("TestUser")).thenAnswer(invocation -> owed.get());
        when(core.consumeSameNameCrackedNotice("TestUser")).thenAnswer(invocation -> owed.getAndSet(false));
        when(storage.loadProfile("TestUser")).thenReturn(record);
        when(record.isSameNameCracked()).thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(storage).withNameLock(eq("TestUser"), any(Runnable.class));
        player = mock(ProxiedPlayer.class);
        connection = mock(Server.class);
        serverInfo = mock(ServerInfo.class);
        when(serverInfo.getName()).thenReturn("lobby");
        when(connection.getInfo()).thenReturn(serverInfo);
        when(player.getName()).thenReturn("TestUser");
        when(player.getAddress()).thenReturn(address);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isConnected()).thenReturn(true);
        when(player.getServer()).thenReturn(connection);
        inbound = mock(PendingConnection.class);
        when(inbound.getAddress()).thenReturn(address);
        delivery = new CrackedNoticeDelivery(plugin, Ticker.systemTicker(), () -> proxyId);
        markRenderingServer();
    }

    private CrackedNoticeClaim prepare() {
        return delivery.prepare(inbound, "TestUser", playerId);
    }

    private void markRenderingServer() {
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", playerId, proxyId.toString(), UUID.randomUUID())));
    }

    private PluginMessageEvent event(CrackedNoticeWire wire) {
        PluginMessageEvent event = mock(PluginMessageEvent.class);
        when(event.getTag()).thenReturn("fastloginplus:" + CrackedNoticeWire.CHANNEL);
        when(event.getSender()).thenReturn(connection);
        when(event.getReceiver()).thenReturn(player);
        when(event.getData()).thenReturn(wire.encode());
        return event;
    }

    @Test
    void reserveDoesNotConsumeAndAckSettlesWithoutProxyChatOrFallbackWarning() {
        CrackedNoticeClaim claim = prepare();
        assertEquals(claim.encode(), prepare().encode());
        delivery.deliver(player, connection);
        delivery.deliver(player, connection);
        assertTrue(owed.get());
        PluginMessageEvent event = event(claim.acknowledgement(proxyId.toString()));
        delivery.onPluginMessage(event);
        verify(event).setCancelled(true);
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        timeout.run();
        assertFalse(owed.get());
        verify(core).consumeSameNameCrackedNotice("TestUser");
        verify(core, never()).sendLocaleMessage(anyString(), any());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void ackBeforeJoinTaskAlsoSettlesAndPreventsTimer() {
        CrackedNoticeClaim claim = prepare();
        when(player.getServer()).thenReturn(null);
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        when(player.getServer()).thenReturn(connection);
        delivery.deliver(player, connection);
        assertFalse(owed.get());
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void channelAndWireFormatMatchTheSharedProtocol() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, connection);
        PluginMessageEvent otherChannel = event(claim.acknowledgement(proxyId.toString()));
        when(otherChannel.getTag()).thenReturn("fastloginplus:force");
        delivery.onPluginMessage(otherChannel);
        verify(otherChannel, never()).setCancelled(true);
        assertTrue(owed.get());
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        assertFalse(owed.get());
    }

    @Test
    void markedBackendWithLostAckUsesProxyFallbackOnceAndWarns() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, connection);
        assertTrue(owed.get());
        timeout.run();
        timeout.run();
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        delivery.deliver(player, connection);
        assertFalse(owed.get());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(plugin.getLog()).warn(contains("did not acknowledge"), eq("TestUser"));
    }

    @Test
    void untrustedOrMissingSourceProxyIdsCannotSettleAnAcknowledgement() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, connection);
        delivery.onPluginMessage(event(claim.acknowledgement(UUID.randomUUID().toString())));
        delivery.onPluginMessage(event(claim.acknowledgement("")));
        assertTrue(owed.get());
        verify(plugin.getLog(), atLeastOnce()).warn("Ignoring unauthenticated cracked-notice acknowledgement");
    }

    @Test
    void malformedTruncatedAndOversizedMessagesAreRejectedWithoutConsuming() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, connection);
        byte[] encoded = claim.acknowledgement(proxyId.toString()).encode();
        for (int length = 0; length < encoded.length; length++) {
            PluginMessageEvent truncated = event(claim.acknowledgement(proxyId.toString()));
            when(truncated.getData()).thenReturn(Arrays.copyOf(encoded, length));
            assertDoesNotThrow(() -> delivery.onPluginMessage(truncated));
        }
        PluginMessageEvent oversized = event(claim.acknowledgement(proxyId.toString()));
        when(oversized.getData()).thenReturn(new byte[CrackedNoticeWire.MAX_BYTES + 1]);
        assertDoesNotThrow(() -> delivery.onPluginMessage(oversized));
        PluginMessageEvent unsupportedVersion = event(claim.acknowledgement(proxyId.toString()));
        when(unsupportedVersion.getData()).thenReturn(new byte[]{2, 3, 0, 1, 'A'});
        assertDoesNotThrow(() -> delivery.onPluginMessage(unsupportedVersion));
        assertTrue(owed.get());
        timeout.run();
        assertFalse(owed.get());
    }

    @Test
    void capableMarkerMakesTheNextDeliveryWaitForAckThenFallsBackOnce() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, connection);
        verify(scheduler).runAsyncDelayed(any(), eq(Duration.ofSeconds(10)));
        assertTrue(owed.get());
        timeout.run();
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        assertFalse(owed.get());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
    }

    @Test
    void clientAnnouncementCannotMarkARegisteredServer() {
        delivery = new CrackedNoticeDelivery(plugin, Ticker.systemTicker(), () -> proxyId);
        PluginMessageEvent client = event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", playerId, proxyId.toString(), UUID.randomUUID()));
        when(client.getSender()).thenReturn(player);
        delivery.onPluginMessage(client);
        prepare();
        delivery.deliver(player, connection);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void missingForwardedPropertyFallsBackToImmediateProxyChat() {
        delivery = new CrackedNoticeDelivery(plugin, Ticker.systemTicker(), () -> proxyId);
        prepare();
        delivery.deliver(player, connection);
        delivery.deliver(player, connection);
        assertFalse(owed.get());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void unconfirmedObservationDoesNotInventDebtOrNonce() {
        owed.set(false);
        assertNull(prepare());
        delivery.deliver(player, connection);
        verifyNoInteractions(storage);
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
    }

    @Test
    void differentCarrierOrBackendConnectionCannotSettleAnArmedAttempt() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, connection);
        PluginMessageEvent other = event(claim.acknowledgement(proxyId.toString()));
        Server oldBackend = mock(Server.class);
        when(other.getSender()).thenReturn(oldBackend);
        delivery.onPluginMessage(other);
        assertTrue(owed.get());
        ProxiedPlayer otherPlayer = mock(ProxiedPlayer.class);
        when(other.getReceiver()).thenReturn(otherPlayer);
        delivery.onPluginMessage(other);
        assertTrue(owed.get());
    }

    @Test
    void disconnectPreservesNameDebtAndReconnectGetsDifferentNonce() {
        CrackedNoticeClaim old = prepare();
        delivery.deliver(player, connection);
        Runnable oldTimeout = timeout;
        delivery.disconnected(player);
        oldTimeout.run();
        CrackedNoticeClaim next = prepare();
        assertNotEquals(old.encode(), next.encode());
        delivery.onPluginMessage(event(old.acknowledgement(proxyId.toString())));
        assertTrue(owed.get());
        delivery.deliver(player, connection);
        delivery.onPluginMessage(event(next.acknowledgement(proxyId.toString())));
        assertFalse(owed.get());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void clearedRecordSuppressesStaleNoticeAndReadFailurePreservesDebt() {
        prepare();
        delivery.deliver(player, connection);
        when(storage.loadProfile("TestUser")).thenReturn(null);
        timeout.run();
        assertTrue(owed.get());
        when(storage.loadProfile("TestUser")).thenReturn(record);
        when(record.isSameNameCracked()).thenReturn(false);
        timeout.run();
        assertFalse(owed.get());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void ackRacingTimeoutHasOneProxySettlement() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, connection);
        java.util.stream.IntStream.range(0, 16).parallel().forEach(index -> {
            if (index % 2 == 0) {
                timeout.run();
            } else {
                delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
            }
        });
        verify(core).consumeSameNameCrackedNotice("TestUser");
        verify(core, atMostOnce()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void renderingMarkerExpiresAfterTwelveHours() {
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        delivery = new CrackedNoticeDelivery(plugin, new Ticker() {
            @Override
            public long read() {
                return nanos.get();
            }
        }, () -> proxyId);
        markRenderingServer();
        nanos.set(Duration.ofHours(12).plusNanos(1).toNanos());
        prepare();
        delivery.deliver(player, connection);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
    }
}
