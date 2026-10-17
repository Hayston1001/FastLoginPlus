/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test: pendingConfirms must be a concurrent set —
 * proxy-side plugin-message listeners run on Netty event-loop threads of
 * different players, and the premium-warning gate relies on the atomic
 * single-winner semantics of add().
 */
class FastLoginCoreTest {

    @Test
    void pendingConfirmsAddShouldHaveSingleWinnerUnderConcurrency() throws Exception {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);
        UUID player = UUID.randomUUID();
        int threads = 16;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> core.getPendingConfirms().add(player));
            }
            List<Future<Boolean>> results = pool.invokeAll(tasks);
            int winners = 0;
            for (Future<Boolean> future : results) {
                if (future.get()) {
                    winners++;
                }
            }
            // atomic check-and-add: exactly one concurrent toggle may pass the gate
            assertEquals(1, winners);
            assertTrue(core.getPendingConfirms().contains(player));

            // repeated add() must be rejected (gate stays closed until remove())
            assertFalse(core.getPendingConfirms().add(player));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void antibotConfigValidationFallsBackToDefaults() {
        // zero/negative anti-bot limits and durations would
        // dead-lock or silently disable the checks — fall back to defaults
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FastLoginCoreTest.class);
        assertEquals(600, FastLoginCore.validatedLimit(logger, "connections", 0, 600));
        assertEquals(10, FastLoginCore.validatedLimit(logger, "burst-limit", -5, 10));
        assertEquals(10, FastLoginCore.validatedLimit(logger, "burst-limit", 10, 10));
        assertEquals(300_000L, FastLoginCore.validatedDurationMs(logger, "expire", 0, 300_000L));
        assertEquals(600_000L, FastLoginCore.validatedDurationMs(logger, "expire", 600_000L, 300_000L));
    }

    // ---- the settled verification verdicts (F-13) ----

    @Test
    void settleCountsOnlyWhenTheSessionServerAnswers() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser");
        core.settlePendingVerifications(true);
        assertEquals(1, core.getDefiniteFailures("TestUser"),
                "the session server answers: the client itself could not verify");

        core.markPendingVerification("OtherUser");
        core.settlePendingVerifications(false);
        assertEquals(0, core.getDefiniteFailures("OtherUser"),
                "an authentication outage is not a failed identity");
        core.settlePendingVerifications(true);
        assertEquals(0, core.getDefiniteFailures("OtherUser"),
                "an outage attempt must not be charged when the session server recovers");
    }

    @Test
    void firstSweepCountsFreshPendingWithoutWaiting() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser");
        core.settlePendingVerifications(true);

        assertEquals(1, core.getDefiniteFailures("TestUser"), "the next sweep settles even a fresh attempt");
        core.settlePendingVerifications(true);
        assertEquals(1, core.getDefiniteFailures("TestUser"), "a sweep must not charge the same attempt twice");
    }

    @Test
    void quickRetryDoesNotPushTheSettlementAway() {
        // Repeated requests share a mark and cannot postpone settlement.
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser");
        core.markPendingVerification("TESTUSER");
        core.settlePendingVerifications(true);

        assertEquals(1, core.getDefiniteFailures("TestUser"));
    }

    @Test
    void definiteVerdictCountsAndClearsThePending() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser");
        core.recordDefiniteFailure("TestUser");
        assertEquals(1, core.getDefiniteFailures("TESTUSER"),
                "a definitive verdict is name-keyed and case-insensitive");

        // the settlement must not count it a second time
        core.settlePendingVerifications(true);
        assertEquals(1, core.getDefiniteFailures("TestUser"));
    }

    @Test
    void droppedAttemptsAndVerificationsCountNothing() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("Dropped");
        core.dropPendingVerification("Dropped");
        core.settlePendingVerifications(true);
        assertEquals(0, core.getDefiniteFailures("Dropped"), "a dropped attempt counts nothing");

        core.recordDefiniteFailure("Verified");
        core.resetVerificationState("Verified");
        assertEquals(0, core.getDefiniteFailures("Verified"),
                "a passing verification refutes everything recorded");
    }

    @Test
    void lateSuccessClearsSettledAndExplicitFailuresAndPendingRetries() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser");
        core.settlePendingVerifications(true);
        core.recordDefiniteFailure("TESTUSER");
        assertEquals(2, core.getDefiniteFailures("testuser"));
        core.markPendingVerification("TestUser");
        core.resetVerificationState("TESTUSER");
        core.settlePendingVerifications(true);

        assertEquals(0, core.getDefiniteFailures("TestUser"), "late success refutes every recorded failure");
    }

    @Test
    void successAndSettlementCannotLeaveAStaleFailureUnderConcurrency() throws Exception {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 200; attempt++) {
                core.markPendingVerification("TestUser");
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> settlement = pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    core.settlePendingVerifications(true);
                    return null;
                });
                Future<?> success = pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    core.resetVerificationState("TESTUSER");
                    return null;
                });
                settlement.get(5, TimeUnit.SECONDS);
                success.get(5, TimeUnit.SECONDS);
                assertEquals(0, core.getDefiniteFailures("testuser"));
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void sameNameCrackedNoticeIsConsumedOnce() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.queueSameNameCrackedNotice("TestUser");
        assertTrue(core.consumeSameNameCrackedNotice("testuser"),
                "the queued notice is name-keyed and case-insensitive");
        assertFalse(core.consumeSameNameCrackedNotice("TestUser"),
                "the one-time notice must not be delivered twice");
    }
}
