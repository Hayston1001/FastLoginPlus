/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingConfirmationCallbackTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void acceptedResponseQueuedBeforeDisconnectCannotTransfer() throws Exception {
        var fixture = new Fixture();
        var request = fixture.register();
        fixture.accept();
        when(fixture.player.connection.isAcceptingMessages()).thenReturn(false);
        BindingCookieProof.drop((BindingCookieProof.ConnectionAccess) () -> fixture.connection);
        BindingCookieProof.expireStoreRequest(fixture.connection, request);
        fixture.drain();
        verify(fixture.player.connection, never()).send(any(ClientboundTransferPacket.class));
        assertSame(fixture.pending, fixture.pendingBindings.get("Player"));
    }

    @Test
    void acceptedOldResponseQueuedBeforeNewAttemptCannotTransfer() throws Exception {
        var fixture = new Fixture();
        fixture.register();
        fixture.accept();
        var replacement = fixture.replacePending();
        fixture.drain();
        verify(fixture.player.connection, never()).send(any(ClientboundTransferPacket.class));
        assertSame(replacement, fixture.pendingBindings.get("Player"));
    }

    @Test
    void disconnectFailureQueuedBeforeReplacementCannotCancelNewAttempt() throws Exception {
        var fixture = new Fixture();
        var request = fixture.register();
        BindingCookieProof.drop((BindingCookieProof.ConnectionAccess) () -> fixture.connection);
        var replacement = fixture.replacePending();
        BindingCookieProof.expireStoreRequest(fixture.connection, request);
        fixture.accept();
        fixture.drain();
        assertSame(replacement, fixture.pendingBindings.get("Player"));
        verify(fixture.player, never()).sendSystemMessage(any());
        verify(fixture.player.connection, never()).send(any(ClientboundTransferPacket.class));
    }

    @Test
    void queuedConfirmationCannotTransferAfterWindowExpires() throws Exception {
        var fixture = new Fixture();
        fixture.register();
        fixture.accept();
        fixture.clock.addAndGet(TimeUnit.MINUTES.toNanos(10));
        fixture.drain();
        verify(fixture.player.connection, never()).send(any(ClientboundTransferPacket.class));
    }

    @Test
    void currentConfirmationTransfersOnceDespiteDropTimeoutAndDuplicateResponse() throws Exception {
        var fixture = new Fixture();
        var request = fixture.register();
        fixture.accept();
        BindingCookieProof.drop((BindingCookieProof.ConnectionAccess) () -> fixture.connection);
        BindingCookieProof.expireStoreRequest(fixture.connection, request);
        fixture.accept();
        fixture.drain();
        verify(fixture.player.connection).send(new ClientboundTransferPacket("127.0.0.1", 25677));
        assertSame(fixture.pending, fixture.pendingBindings.get("Player"));
    }

    private static final class Fixture {
        private final AtomicLong clock = new AtomicLong(100);
        private final EasyAuth344Bridge.PendingBinding pending = pending();
        private final ConcurrentHashMap<String, EasyAuth344Bridge.PendingBinding> pendingBindings =
                new ConcurrentHashMap<>();
        private final EasyAuth344Bridge bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        private final ServerPlayer player = mock(ServerPlayer.class);
        private final Connection connection = mock(Connection.class);
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();

        private Fixture() throws Exception {
            var field = EasyAuth344Bridge.class.getDeclaredField("pendingBindings");
            field.setAccessible(true);
            field.set(bridge, pendingBindings);
            pendingBindings.put("Player", pending);
            player.connection = mock(ServerGamePacketListenerImpl.class);
            when(player.connection.isAcceptingMessages()).thenReturn(true);
        }

        private EasyAuth344Bridge.PendingBinding pending() {
            return new EasyAuth344Bridge.PendingBinding(new UUID(0, 1), "test-hash", new byte[]{1}, clock::get);
        }

        private EasyAuth344Bridge.PendingBinding replacePending() {
            var replacement = pending();
            pendingBindings.put("Player", replacement);
            return replacement;
        }

        private BindingCookieProof.StoreRequest register() {
            return BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{1},
                    () -> queue.add(() -> bridge.transferAfterConfirmation(
                            player, "Player", pending, "127.0.0.1", 25677)),
                    () -> queue.add(() -> bridge.failStoredCookie(player, "Player", pending)));
        }

        private void accept() {
            assertTrue(BindingCookieProof.accept(connection,
                    new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{1})));
        }

        private void drain() {
            while (!queue.isEmpty()) {
                queue.remove().run();
            }
        }
    }
}
