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
    void classifiesTheLoginStateOfARejectedAttempt() {
        assertEquals(VerificationCloseWatcher.Outcome.AUTH_FAILED,
                VerificationCloseWatcher.classifyLoginState("ENCRYPTION_RESPONSE_RECEIVED"));
        assertEquals(VerificationCloseWatcher.Outcome.NOT_ANSWERED,
                VerificationCloseWatcher.classifyLoginState("ENCRYPTION_REQUEST_SENT"));
        assertEquals(VerificationCloseWatcher.Outcome.NOT_ANSWERED,
                VerificationCloseWatcher.classifyLoginState("LOGIN_PACKET_RECEIVED"));
        assertEquals(VerificationCloseWatcher.Outcome.NOT_ANSWERED,
                VerificationCloseWatcher.classifyLoginState("LOGIN_PACKET_EXPECTED"));
    }

    @Test
    void unknownStatesLeaveTheGracePeriodInCharge() {
        assertEquals(VerificationCloseWatcher.Outcome.UNKNOWN,
                VerificationCloseWatcher.classifyLoginState(null));
        assertEquals(VerificationCloseWatcher.Outcome.UNKNOWN,
                VerificationCloseWatcher.classifyLoginState("SOMETHING_NEW"));
        assertEquals(VerificationCloseWatcher.Outcome.UNKNOWN,
                VerificationCloseWatcher.classifyHandler(null));
    }
}
