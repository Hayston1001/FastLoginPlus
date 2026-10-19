/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.VelocityLoginSource;
import com.github.games647.fastlogin.velocity.listener.ConnectListener;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginPreLoginEvent;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandshakeDecisionTest {
    private final InboundConnection connection = mock(InboundConnection.class);
    private final PreLoginEvent event = new PreLoginEvent(connection, "Steve");
    private final VelocityLoginSource source = new VelocityLoginSource(connection, event);
    private final FastLoginVelocity plugin = mock(FastLoginVelocity.class);
    private final FastLoginCore core = mock(FastLoginCore.class);

    private AsyncPremiumCheck check() {
        when(connection.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        doReturn(core).when(plugin).getCore();
        when(plugin.getSession()).thenReturn(new ConcurrentHashMap<InetSocketAddress, VelocityLoginSession>());
        return new AsyncPremiumCheck(plugin, connection, "Steve", event);
    }

    @Test
    void premiumDecisionForcesOnline() {
        check().requestPremiumLogin(source, mock(StoredProfile.class), "Steve", true);
        assertTrue(event.getResult().isOnlineModeAllowed());
        assertFalse(event.getResult().isForceOfflineMode());
    }

    @Test
    void offlineDecisionReplacesPriorForcedOnline() {
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        check().startCrackedSession(source, mock(StoredProfile.class), "Steve");
        assertTrue(event.getResult().isForceOfflineMode());
        assertFalse(event.getResult().isOnlineModeAllowed());
    }

    @Test
    void freshOfflineDecisionAlsoForcesOffline() {
        check().startCrackedSession(source, mock(StoredProfile.class), "Steve");
        assertTrue(event.getResult().isForceOfflineMode());
    }

    @Test
    void flpDenialCannotBecomeOfflineOrOnline() {
        source.kick("Rejected");
        Object denied = event.getResult();
        source.enableOfflinemode();
        source.enableOnlinemode();
        assertSame(denied, event.getResult());
        assertFalse(event.getResult().isAllowed());
    }

    @Test
    void externalDenialSkipsTheDecisionTree() {
        AsyncPremiumCheck check = check();
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text("Other plugin")));
        Object denied = event.getResult();
        check.run();
        assertSame(denied, event.getResult());
        verify(core, never()).getStorage();
    }

    @Test
    void constructingAnUndecidedSourceDoesNotChangePriorResult() {
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        Object prior = event.getResult();
        new VelocityLoginSource(connection, event);
        assertSame(prior, event.getResult());
    }

    @Test
    void storageReadFailureLeavesForcedOnlineUntouched() {
        AsyncPremiumCheck check = check();
        SQLStorage storage = mock(SQLStorage.class);
        when(core.getStorage()).thenReturn(storage);
        doAnswer(invocation -> invocation.getArgument(1, Supplier.class).get())
                .when(storage).withNameLock(eq("Steve"), any(Supplier.class));
        when(storage.loadProfile("Steve")).thenReturn(null);
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        Object prior = event.getResult();
        check.run();
        assertSame(prior, event.getResult());
    }

    @Test
    void exceptionBeforeDecisionLeavesResultUntouched() {
        AsyncPremiumCheck check = check();
        when(core.getStorage()).thenThrow(new IllegalStateException("storage unavailable"));
        Object prior = event.getResult();
        assertThrows(IllegalStateException.class, check::run);
        assertSame(prior, event.getResult());
    }

    @Test
    void preLoginEventFailureAbortsWithoutAnOfflineDecision() throws Exception {
        callbackFailure(new ExecutionException(new IllegalStateException("handler failed")));
    }

    @Test
    void preLoginEventTimeoutAbortsWithoutAnOfflineDecision() throws Exception {
        callbackFailure(new TimeoutException("handler timed out"));
    }

    @Test
    void interruptedPreLoginEventAbortsAndRestoresInterrupt() throws Exception {
        try {
            callbackFailure(new InterruptedException("interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void callbackFailure(Exception failure) throws Exception {
        AsyncPremiumCheck check = check();
        ProxyServer proxy = mock(ProxyServer.class);
        EventManager manager = mock(EventManager.class);
        CompletableFuture<VelocityFastLoginPreLoginEvent> future = mock(CompletableFuture.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getEventManager()).thenReturn(manager);
        when(manager.fire(any(VelocityFastLoginPreLoginEvent.class))).thenReturn(future);
        when(future.get(5, TimeUnit.SECONDS)).thenThrow(failure);
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        Object prior = event.getResult();
        assertThrows(IllegalStateException.class,
                () -> check.callFastLoginPreLoginEvent("Steve", source, mock(StoredProfile.class)));
        assertSame(prior, event.getResult());
    }

    @Test
    void reviewedNormalHandlersRunBeforeFlp() throws Exception {
        assertEquals(PostOrder.LAST, ConnectListener.class.getMethod("onPreLogin", PreLoginEvent.class)
                .getAnnotation(Subscribe.class).order());
    }
}
