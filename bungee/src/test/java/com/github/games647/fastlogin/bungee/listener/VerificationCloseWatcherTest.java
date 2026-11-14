/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationCloseWatcherTest {

    @Test
    void classifiesTheLoginPhaseState() {
        assertEquals(VerificationCloseWatcher.Outcome.AUTH_FAILED,
                VerificationCloseWatcher.classify("FINISHING"));
        assertEquals(VerificationCloseWatcher.Outcome.NOT_ANSWERED,
                VerificationCloseWatcher.classify("ENCRYPT"));
        assertEquals(VerificationCloseWatcher.Outcome.NOT_ANSWERED,
                VerificationCloseWatcher.classify("USERNAME"));
    }

    @Test
    void unknownStatesLeaveTheGracePeriodInCharge() {
        assertEquals(VerificationCloseWatcher.Outcome.UNKNOWN,
                VerificationCloseWatcher.classify("SOME_FUTURE_STATE"));
        assertEquals(VerificationCloseWatcher.Outcome.UNKNOWN,
                VerificationCloseWatcher.classify(null));
    }
}
