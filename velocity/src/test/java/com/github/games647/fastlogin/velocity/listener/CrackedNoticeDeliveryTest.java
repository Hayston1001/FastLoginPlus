/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.CrackedNoticeClaim;
import com.github.games647.fastlogin.protocol.CrackedNoticeWire;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
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
    private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
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
    private AsyncScheduler scheduler;
    private InboundConnection inbound;
    private Runnable timeout;

    @BeforeEach
    void setUp() {
        plugin = mock(FastLoginVelocity.class);
        core = mock(FastLoginCore.class);
        storage = mock(SQLStorage.class);
        record = mock(StoredProfile.class);
        scheduler = mock(AsyncScheduler.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getName()).thenReturn("fastloginplus");
        when(plugin.getProxyId()).thenReturn(proxyId);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(scheduler).runAsync(any(Runnable.class));
        doAnswer(invocation -> { timeout = invocation.getArgument(0); return null; })
                .when(scheduler).runAsyncDelayed(any(Runnable.class), any(Duration.class));
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
        when(player.getRemoteAddress()).thenReturn(address);
        inbound = mock(InboundConnection.class);
        when(inbound.getRemoteAddress()).thenReturn(address);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isActive()).thenReturn(true);
        when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        when(connection.getPlayer()).thenReturn(player);
        when(connection.getServer()).thenReturn(target);
        channel = MinecraftChannelIdentifier.create("fastloginplus", CrackedNoticeWire.CHANNEL);
        delivery = new CrackedNoticeDelivery(plugin);
        markRenderingServer();
    }

    private CrackedNoticeClaim prepare() {
        return delivery.prepare(inbound, "TestUser", playerId);
    }

    private void markRenderingServer() {
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", playerId, proxyId.toString(), UUID.randomUUID())));
    }

    private PluginMessageEvent event(CrackedNoticeWire ack) {
        PluginMessageEvent event = mock(PluginMessageEvent.class);
        when(event.getIdentifier()).thenReturn(channel);
        when(event.getResult()).thenReturn(PluginMessageEvent.ForwardResult.forward());
        when(event.getSource()).thenReturn(connection);
        when(event.getTarget()).thenReturn(player);
        when(event.getData()).thenReturn(ack.encode());
        return event;
    }

    @Test
    void reserveDoesNotConsumeAndAckSettlesWithoutProxyChatOrOutboundPayload() {
        CrackedNoticeClaim claim = prepare();
        assertEquals(claim.encode(), prepare().encode());
        delivery.deliver(player, target);
        delivery.deliver(player, target);
        assertTrue(owed.get());
        PluginMessageEvent event = event(claim.acknowledgement(proxyId.toString()));
        delivery.onPluginMessage(event);
        verify(event).setResult(PluginMessageEvent.ForwardResult.handled());
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        timeout.run();
        assertFalse(owed.get());
        verify(core).consumeSameNameCrackedNotice("TestUser");
        verify(core, never()).sendLocaleMessage(anyString(), any());
        verify(connection, never()).sendPluginMessage(any(), any(byte[].class));
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void ackBeforeJoinTaskAlsoSettlesAndPreventsTimer() {
        CrackedNoticeClaim claim = prepare();
        when(player.getCurrentServer()).thenReturn(Optional.empty());
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        delivery.deliver(player, target);
        assertFalse(owed.get());
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void markedBackendWithLostAckUsesProxyFallbackOnceAndWarns() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        assertTrue(owed.get());
        timeout.run();
        timeout.run();
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        delivery.deliver(player, target);
        assertFalse(owed.get());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(plugin.getLog()).warn(contains("did not acknowledge"), eq("TestUser"));
    }

    @Test
    void missingForwardedPropertyKeepsClassicFallbackWithoutInventingAcknowledgement() {
        delivery = new CrackedNoticeDelivery(plugin);
        delivery.deliver(player, target);
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verify(connection, never()).sendPluginMessage(any(), any(byte[].class));
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void clientsWrongNameUuidNonceOrUntrustedEchoCannotConsume() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        PluginMessageEvent client = event(claim.acknowledgement(proxyId.toString()));
        when(client.getSource()).thenReturn(player);
        delivery.onPluginMessage(client);
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.ACK, "OtherUser",
                playerId, proxyId.toString(), UUID.randomUUID())));
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.ACK, "TestUser",
                UUID.randomUUID(), proxyId.toString(), UUID.randomUUID())));
        delivery.onPluginMessage(event(new CrackedNoticeClaim(true, "TestUser", playerId, UUID.randomUUID())
                .acknowledgement(proxyId.toString())));
        delivery.onPluginMessage(event(claim.acknowledgement(UUID.randomUUID().toString())));
        assertTrue(owed.get());
        timeout.run();
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
    }

    @Test
    void differentCarrierOrBackendConnectionCannotSettleAnArmedAttempt() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        PluginMessageEvent other = event(claim.acknowledgement(proxyId.toString()));
        ServerConnection oldBackend = mock(ServerConnection.class);
        when(oldBackend.getPlayer()).thenReturn(player);
        when(other.getSource()).thenReturn(oldBackend);
        delivery.onPluginMessage(other);
        assertTrue(owed.get());
        when(connection.getPlayer()).thenReturn(mock(Player.class));
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        assertTrue(owed.get());
    }

    @Test
    void optionalEchoSwitchDoesNotDisableConnectionOrNonceChecks() {
        core.getConfig().set("verify-backend-messages", false);
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        delivery.onPluginMessage(event(claim.acknowledgement("")));
        assertFalse(owed.get());
    }

    @Test
    void concurrentTasksArmOneTimeoutAndFallbackOnlyOnce() {
        prepare();
        java.util.stream.IntStream.range(0, 16).parallel().forEach(index -> delivery.deliver(player, target));
        verify(scheduler).runAsyncDelayed(any(), eq(Duration.ofSeconds(10)));
        java.util.stream.IntStream.range(0, 16).parallel().forEach(index -> timeout.run());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
    }

    @Test
    void ackRacingTimeoutHasOneProxySettlement() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        java.util.stream.IntStream.range(0, 16).parallel().forEach(index -> {
            if (index % 2 == 0) { timeout.run(); }
            else { delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString()))); }
        });
        verify(core).consumeSameNameCrackedNotice("TestUser");
        verify(core, atMostOnce()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void unconfirmedObservationDoesNotInventDebtOrNonce() {
        owed.set(false);
        assertNull(prepare());
        delivery.deliver(player, target);
        verifyNoInteractions(storage);
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
    }

    @Test
    void disconnectPreservesNameDebtAndReconnectGetsDifferentNonce() {
        CrackedNoticeClaim old = prepare();
        delivery.deliver(player, target);
        Runnable oldTimeout = timeout;
        delivery.disconnected(player);
        oldTimeout.run();
        CrackedNoticeClaim next = prepare();
        assertNotEquals(old.encode(), next.encode());
        delivery.onPluginMessage(event(old.acknowledgement(proxyId.toString())));
        assertTrue(owed.get());
        delivery.deliver(player, target);
        delivery.onPluginMessage(event(next.acknowledgement(proxyId.toString())));
        assertFalse(owed.get());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void disconnectedOrSwitchedConnectionDoesNotSpendDebt() {
        prepare();
        delivery.deliver(player, target);
        when(player.getCurrentServer()).thenReturn(Optional.of(mock(ServerConnection.class)));
        timeout.run();
        assertTrue(owed.get());
        when(player.isActive()).thenReturn(false);
        timeout.run();
        assertTrue(owed.get());
    }

    @Test
    void clearedRecordSuppressesStaleNoticeAndReadFailurePreservesDebt() {
        prepare();
        delivery.deliver(player, target);
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
    void failedFallbackRetainsDebtForReconnectButNeverRetriesThisAttempt() {
        prepare();
        delivery.deliver(player, target);
        doThrow(new IllegalStateException("chat failed")).when(core).sendLocaleMessage(anyString(), any());
        timeout.run();
        timeout.run();
        assertTrue(owed.get());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
    }

    @Test
    void schedulerFailureUsesFallbackWithoutSpendingDebtEarly() {
        prepare();
        doThrow(new IllegalStateException("executor failed")).when(scheduler).runAsyncDelayed(any(), any());
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        assertFalse(owed.get());
    }

    @Test
    void failedAckSchedulingLeavesTimeoutFallbackAvailable() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        doThrow(new IllegalStateException("executor failed")).when(scheduler).runAsync(any());
        assertDoesNotThrow(() -> delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString()))));
        assertTrue(owed.get());
        timeout.run();
        assertFalse(owed.get());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
    }

    @Test
    void malformedAckDoesNotConsumeOrInterfereWithFallback() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        PluginMessageEvent malformed = event(claim.acknowledgement(proxyId.toString()));
        when(malformed.getData()).thenReturn(new byte[]{1, 0});
        assertDoesNotThrow(() -> delivery.onPluginMessage(malformed));
        assertTrue(owed.get());
        timeout.run();
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
    }

    @Test
    void disconnectWhileStorageWasBlockedDoesNotSpendDebt() {
        prepare();
        delivery.deliver(player, target);
        when(storage.loadProfile("TestUser")).thenAnswer(invocation -> {
            when(player.isActive()).thenReturn(false);
            return record;
        });
        timeout.run();
        assertTrue(owed.get());
        verify(core, never()).sendLocaleMessage(anyString(), any());
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
    }

    @Test
    void reusedAddressGetsFreshNonceAndOldDisconnectCannotRemoveNewUnboundAttempt() {
        CrackedNoticeClaim old = prepare();
        delivery.deliver(player, target);
        Runnable oldTimeout = timeout;
        InboundConnection nextInbound = mock(InboundConnection.class);
        when(nextInbound.getRemoteAddress()).thenReturn(address);
        CrackedNoticeClaim next = delivery.prepare(nextInbound, "TestUser", playerId);
        assertNotEquals(old.encode(), next.encode());
        delivery.disconnected(player);
        oldTimeout.run();
        assertTrue(owed.get());
        delivery.onPluginMessage(event(old.acknowledgement(proxyId.toString())));
        assertTrue(owed.get());
        delivery.deliver(player, target);
        delivery.onPluginMessage(event(next.acknowledgement(proxyId.toString())));
        assertFalse(owed.get());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void coldStartWithForwardedClaimStillUsesImmediateQuietProxyChat() {
        delivery = new CrackedNoticeDelivery(plugin);
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        assertFalse(owed.get());
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verifyNoInteractions(plugin.getLog());
        markRenderingServer();
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())));
        delivery.deliver(player, target);
        verify(core).consumeSameNameCrackedNotice("TestUser");
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
    }

    @Test
    void announcementWithoutDebtMarksServerButNeverConsumesOrAuthorizesAcknowledgement() {
        delivery = new CrackedNoticeDelivery(plugin);
        owed.set(false);
        markRenderingServer();
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        verifyNoInteractions(storage);
        owed.set(true);
        prepare();
        delivery.deliver(player, target);
        assertTrue(owed.get());
        verify(scheduler).runAsyncDelayed(any(), eq(Duration.ofSeconds(10)));
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void renderingMarkerWithThePendingNonceIsStillNotAnAcknowledgement() {
        CrackedNoticeClaim claim = prepare();
        delivery.deliver(player, target);
        delivery.onPluginMessage(event(claim.acknowledgement(proxyId.toString())
                .withKind(CrackedNoticeWire.Kind.CAPABLE)));
        assertTrue(owed.get());
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        timeout.run();
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(plugin.getLog()).warn(contains("did not acknowledge"), eq("TestUser"));
    }

    @Test
    void markerIsServerScopedAndDoesNotMakeAnotherBackendWait() {
        RegisteredServer other = mock(RegisteredServer.class);
        when(connection.getServer()).thenReturn(other);
        prepare();
        delivery.deliver(player, other);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void authenticatedMarkerSurvivesCarrierDisconnectWithoutConsumingNotice() {
        delivery = new CrackedNoticeDelivery(plugin);
        // A validated hint is not an ACK and requires no pending notice nonce.
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", playerId, proxyId.toString(), UUID.randomUUID())));
        delivery.disconnected(player);
        prepare();
        delivery.deliver(player, target);
        assertTrue(owed.get());
        verify(scheduler).runAsyncDelayed(any(), eq(Duration.ofSeconds(10)));
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void clientAnnouncementCannotMarkARegisteredServer() {
        delivery = new CrackedNoticeDelivery(plugin);
        PluginMessageEvent client = event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", playerId, proxyId.toString(), UUID.randomUUID()));
        when(client.getSource()).thenReturn(player);
        delivery.onPluginMessage(client);
        prepare();
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void renderingMarkerExpiresAfterTwelveHours() {
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        delivery = new CrackedNoticeDelivery(plugin, new com.google.common.base.Ticker() {
            @Override
            public long read() { return nanos.get(); }
        });
        markRenderingServer();
        nanos.set(Duration.ofHours(12).plusNanos(1).toNanos());
        prepare();
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void eachAnnouncementRefreshesRenderingMarkerTtl() {
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        delivery = new CrackedNoticeDelivery(plugin, new com.google.common.base.Ticker() {
            @Override
            public long read() { return nanos.get(); }
        });
        markRenderingServer();
        nanos.set(Duration.ofHours(11).toNanos());
        markRenderingServer();
        nanos.set(Duration.ofHours(22).toNanos());
        prepare();
        delivery.deliver(player, target);
        verify(scheduler).runAsyncDelayed(any(), eq(Duration.ofSeconds(10)));
        assertTrue(owed.get());
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "untrusted"})
    void missingOrUntrustedMarkerEchoWarnsAndCannotMakeServerWait(String echo) {
        delivery = new CrackedNoticeDelivery(plugin);
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", playerId, echo, UUID.randomUUID())));
        verify(plugin.getLog()).warn("Ignoring unauthenticated cracked-notice backend capability");
        verifyNoInteractions(storage);
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        prepare();
        delivery.deliver(player, target);
        verify(core).sendLocaleMessage("same-name-cracked-recorded", player);
        verify(scheduler, never()).runAsyncDelayed(any(), any());
        verifyNoMoreInteractions(plugin.getLog());
    }

    @Test
    void disabledEchoVerificationStillAllowsServerRenderingHintWithoutEcho() {
        delivery = new CrackedNoticeDelivery(plugin);
        core.getConfig().set("verify-backend-messages", false);
        delivery.onPluginMessage(event(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", playerId, "", UUID.randomUUID())));
        prepare();
        delivery.deliver(player, target);
        assertTrue(owed.get());
        verify(scheduler).runAsyncDelayed(any(), eq(Duration.ofSeconds(10)));
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        verifyNoInteractions(plugin.getLog());
    }

    @Test
    void renderingMarkerStaysWarmBeyondTenMinutesUntilTwelveHourExpiry() {
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        delivery = new CrackedNoticeDelivery(plugin, new com.google.common.base.Ticker() {
            @Override
            public long read() { return nanos.get(); }
        });
        markRenderingServer();
        nanos.set(Duration.ofHours(12).minusNanos(1).toNanos());
        prepare();
        delivery.deliver(player, target);
        assertTrue(owed.get());
        verify(scheduler).runAsyncDelayed(any(), eq(Duration.ofSeconds(10)));
        verify(core, never()).sendLocaleMessage(anyString(), any());
        verifyNoInteractions(plugin.getLog());
    }
}
