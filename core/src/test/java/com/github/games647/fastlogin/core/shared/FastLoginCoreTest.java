/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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

        core.markPendingVerification("TestUser", 0L);
        core.settlePendingVerifications(FastLoginCore.PENDING_VERIFICATION_GRACE_MS + 1, true);
        assertEquals(1, core.getDefiniteFailures("TestUser"),
                "the session server answers: the client itself could not verify");

        core.markPendingVerification("OtherUser", 0L);
        core.settlePendingVerifications(FastLoginCore.PENDING_VERIFICATION_GRACE_MS + 1, false);
        assertEquals(0, core.getDefiniteFailures("OtherUser"),
                "an authentication outage is not a failed identity");
    }

    @Test
    void settleIgnoresFreshPendings() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser", 0L);
        core.settlePendingVerifications(FastLoginCore.PENDING_VERIFICATION_GRACE_MS - 1, true);

        assertEquals(0, core.getDefiniteFailures("TestUser"), "within Δ the attempt is not judged yet");
    }

    @Test
    void quickRetryDoesNotPushTheSettlementAway() {
        // the settlement is judged from the first attempt: a player hammering
        // reconnects must not postpone it forever
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser", 0L);
        core.markPendingVerification("TestUser", FastLoginCore.PENDING_VERIFICATION_GRACE_MS);
        core.settlePendingVerifications(FastLoginCore.PENDING_VERIFICATION_GRACE_MS + 1, true);

        assertEquals(1, core.getDefiniteFailures("TestUser"));
    }

    @Test
    void definiteVerdictCountsAndClearsThePending() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("TestUser", 0L);
        core.recordDefiniteFailure("TestUser");
        assertEquals(1, core.getDefiniteFailures("TESTUSER"),
                "a definitive verdict is name-keyed and case-insensitive");

        // the settlement must not count it a second time
        core.settlePendingVerifications(FastLoginCore.PENDING_VERIFICATION_GRACE_MS + 1, true);
        assertEquals(1, core.getDefiniteFailures("TestUser"));
    }

    @Test
    void droppedAttemptsAndVerificationsCountNothing() {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.markPendingVerification("Dropped", 0L);
        core.dropPendingVerification("Dropped");
        core.settlePendingVerifications(FastLoginCore.PENDING_VERIFICATION_GRACE_MS + 1, true);
        assertEquals(0, core.getDefiniteFailures("Dropped"), "a dropped attempt counts nothing");

        core.recordDefiniteFailure("Verified");
        core.resetVerificationState("Verified");
        assertEquals(0, core.getDefiniteFailures("Verified"),
                "a passing verification refutes everything recorded");
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
