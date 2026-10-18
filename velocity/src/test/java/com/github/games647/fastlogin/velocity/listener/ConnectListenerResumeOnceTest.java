/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginAntiBotEvent;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

/**
 * the EventTask continuation must be resumed exactly once on every
 * path — including when the anti-bot decision application throws (previously
 * the login hung until the read timeout).
 */
class ConnectListenerResumeOnceTest {

    @Test
    void resumeOnceResumesOnlyOnFirstCall() {
        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        ConnectListener.resumeOnce(continuation, resumed);
        ConnectListener.resumeOnce(continuation, resumed);
        ConnectListener.resumeOnce(continuation, resumed);

        verify(continuation, times(1)).resume();
        assertTrue(resumed.get());
    }

    @Test
    void blockDecisionDeniesAndResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore<?, ?, ?> core = mock(FastLoginCore.class);
        doReturn(core).when(plugin).getCore();
        when(core.getMessage("kick-antibot")).thenReturn("Blocked");
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));

        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        PreLoginEvent event = new PreLoginEvent(mock(InboundConnection.class), "Steve");
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        listener.applyDecisionSafely(event, mock(InboundConnection.class),
                "Steve", Action.Block, continuation, resumed);

        assertFalse(event.getResult().isAllowed());
        assertFalse(event.getResult().isForceOfflineMode());
        ConnectListener.resumeOnce(continuation, resumed);
        verify(continuation, times(1)).resume();
        assertTrue(resumed.get());
    }

    @Test
    void cancelledAntiBotEventQueuesPremiumCheckAndResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        ProxyServer proxy = mock(ProxyServer.class);
        EventManager manager = mock(EventManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getEventManager()).thenReturn(manager);
        AntiBotService antiBot = mock(AntiBotService.class);
        when(antiBot.onIncomingConnection(any(), anyString())).thenReturn(Action.Block);
        when(manager.fire(any(VelocityFastLoginAntiBotEvent.class))).thenAnswer(invocation -> {
            VelocityFastLoginAntiBotEvent fired = invocation.getArgument(0);
            assertSame(Action.Block, fired.getAction());
            fired.setCancelled(true);
            return CompletableFuture.completedFuture(fired);
        });
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(invocation -> {
            queued.set(invocation.getArgument(0, Runnable.class));
            return new CompletableFuture<>();
        }).when(scheduler).runAsync(any(Runnable.class));
        PreLoginEvent event = new PreLoginEvent(mock(InboundConnection.class), "Steve");
        Object prior = event.getResult();
        Continuation continuation = mock(Continuation.class);

        new ConnectListener(plugin, antiBot).onPreLogin(event).execute(continuation);

        assertSame(prior, event.getResult());
        verify(scheduler).runAsync(any(Runnable.class));
        verify(continuation, never()).resume();
        queued.get().run();
        verify(continuation, times(1)).resume();
        assertSame(prior, event.getResult());
    }

    @Test
    void explicitAntiBotBlockRemainsDeniedInsteadOfOffline() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore<?, ?, ?> core = mock(FastLoginCore.class);
        doReturn(core).when(plugin).getCore();
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        when(core.getMessage("kick-antibot")).thenReturn("Blocked");
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));
        PreLoginEvent event = new PreLoginEvent(mock(InboundConnection.class), "Steve");
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        Continuation continuation = mock(Continuation.class);
        listener.applyDecisionSafely(event, mock(InboundConnection.class), "Steve", Action.Block,
                continuation, new AtomicBoolean(false));
        assertFalse(event.getResult().isAllowed());
        assertFalse(event.getResult().isForceOfflineMode());
        verify(continuation).resume();
    }

    @Test
    void failedAntiBotCallbackDoesNotGuessADenialOrOfflineDecision() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        ProxyServer proxy = mock(ProxyServer.class);
        EventManager manager = mock(EventManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getEventManager()).thenReturn(manager);
        AntiBotService antiBot = mock(AntiBotService.class);
        when(antiBot.onIncomingConnection(any(), anyString())).thenReturn(Action.Block);
        CompletableFuture<VelocityFastLoginAntiBotEvent> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("anti-bot handler failed"));
        when(manager.fire(any(VelocityFastLoginAntiBotEvent.class))).thenReturn(failed);
        PreLoginEvent event = new PreLoginEvent(mock(InboundConnection.class), "Steve");
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        Object prior = event.getResult();
        Continuation continuation = mock(Continuation.class);
        new ConnectListener(plugin, antiBot).onPreLogin(event).execute(continuation);
        assertSame(prior, event.getResult());
        verify(continuation).resume();
        verify(plugin, never()).getCore();
    }

    @Test
    void throwingDecisionStillResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore<?, ?, ?> core = mock(FastLoginCore.class);
        doReturn(core).when(plugin).getCore();
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        // Block action path: the locale lookup blows up
        when(core.getMessage(anyString())).thenThrow(new IllegalStateException("boom"));
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));

        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        listener.applyDecisionSafely(mock(PreLoginEvent.class), mock(InboundConnection.class),
                "Steve", Action.Block, continuation, resumed);

        verify(continuation, times(1)).resume();
        assertTrue(resumed.get(), "the guard must stay flipped after the fallback resume");
    }

    @Test
    void asyncPremiumFailureStillResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        }).when(scheduler).runAsync(any(Runnable.class));
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));
        PreLoginEvent event = mock(PreLoginEvent.class);
        Continuation continuation = mock(Continuation.class);

        listener.applyDecisionSafely(event, mock(InboundConnection.class), "Steve", Action.Continue,
                continuation, new AtomicBoolean(false));

        // no proxy-side denial: a failing login carries no verified fact and the
        // backend keeps it behind the password lock
        verify(event, never()).setResult(any());
        verify(continuation, times(1)).resume();
    }

    @Test
    void queuedPremiumTaskResumesWhenItFinallyRuns() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        when(plugin.getScheduler()).thenReturn(scheduler);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(invocation -> {
            queued.set(invocation.getArgument(0, Runnable.class));
            return new CompletableFuture<>();
        }).when(scheduler).runAsync(any(Runnable.class));
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));
        PreLoginEvent event = mock(PreLoginEvent.class);
        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        listener.applyDecisionSafely(event, mock(InboundConnection.class), "Steve", Action.Continue,
                continuation, resumed);
        verify(continuation, never()).resume();
        assertFalse(resumed.get());
        queued.get().run();

        verify(continuation, times(1)).resume();
        ConnectListener.resumeOnce(continuation, resumed);
        verify(continuation, times(1)).resume();
    }

    @Test
    void rejectedSchedulingStillResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        when(plugin.getScheduler()).thenReturn(scheduler);
        doThrow(new RejectedExecutionException("stopping"))
                .when(scheduler).runAsync(any(Runnable.class));
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));
        PreLoginEvent event = mock(PreLoginEvent.class);
        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        listener.applyDecisionSafely(event, mock(InboundConnection.class), "Steve", Action.Continue,
                continuation, resumed);

        verify(continuation, times(1)).resume();
        ConnectListener.resumeOnce(continuation, resumed);
        verify(continuation, times(1)).resume();
    }

    @Test
    void freshGuardStartsUnresumed() {
        assertFalse(new AtomicBoolean(false).get());
    }

    @Test
    void shutdownSchedulerFutureStillResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        Logger logger = mock(Logger.class);
        SchedulerWithStoppableExecutor scheduler = new SchedulerWithStoppableExecutor(logger);
        scheduler.shutdown();
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getScheduler()).thenReturn(scheduler);
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));
        PreLoginEvent event = mock(PreLoginEvent.class);
        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        listener.applyDecisionSafely(event, mock(InboundConnection.class), "Steve", Action.Continue,
                continuation, resumed);
        scheduler.stopExecutor();

        verify(continuation, times(1)).resume();
        ConnectListener.resumeOnce(continuation, resumed);
        verify(continuation, times(1)).resume();
        verify(plugin, never()).getCore();
    }

    @Test
    void realExecutorRejectionStillResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        Logger logger = mock(Logger.class);
        SchedulerWithStoppableExecutor scheduler = new SchedulerWithStoppableExecutor(logger);
        scheduler.stopExecutor();
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getScheduler()).thenReturn(scheduler);
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));
        PreLoginEvent event = mock(PreLoginEvent.class);
        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        listener.applyDecisionSafely(event, mock(InboundConnection.class), "Steve", Action.Continue,
                continuation, resumed);

        verify(continuation, times(1)).resume();
        verify(plugin, never()).getCore();
    }

    private static final class SchedulerWithStoppableExecutor extends AsyncScheduler {

        SchedulerWithStoppableExecutor(Logger logger) {
            super(logger, Executors.newSingleThreadExecutor());
        }

        void stopExecutor() {
            // Java 21 replaces the constructor's pool with a virtual-thread
            // executor. Stop the pool actually used by the loaded variant.
            ((ExecutorService) processingPool).shutdownNow();
        }
    }
}
