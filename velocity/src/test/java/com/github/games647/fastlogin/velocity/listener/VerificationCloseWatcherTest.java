/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationCloseWatcherTest {

    @Test
    void classifiesTheLoginPhaseHandler() {
        assertEquals(VerificationCloseWatcher.Outcome.NOT_ANSWERED,
                VerificationCloseWatcher.classify("InitialLoginSessionHandler"));
        assertEquals(VerificationCloseWatcher.Outcome.AUTH_FAILED,
                VerificationCloseWatcher.classify("AuthSessionHandler"));
    }

    @Test
    void unknownHandlersLeaveTheGracePeriodInCharge() {
        assertEquals(VerificationCloseWatcher.Outcome.UNKNOWN,
                VerificationCloseWatcher.classify("ClientPlaySessionHandler"));
        assertEquals(VerificationCloseWatcher.Outcome.UNKNOWN,
                VerificationCloseWatcher.classify(null));
    }
}
