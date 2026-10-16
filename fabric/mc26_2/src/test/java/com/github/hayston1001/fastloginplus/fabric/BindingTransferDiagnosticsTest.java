/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;

import static com.github.hayston1001.fastloginplus.fabric.BindingTransferDiagnostics.Reason.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BindingTransferDiagnosticsTest {
    @Test
    void eachGuardHasAnObservedReasonAndAllPassingStillTransfers() {
        assertEquals(PENDING_REPLACED_OR_REMOVED,
                BindingTransferDiagnostics.skipReason(false, false, false, true));
        assertEquals(PENDING_EXPIRED,
                BindingTransferDiagnostics.skipReason(true, false, false, true));
        assertEquals(PLAYER_DISCONNECTED_OR_KICKED,
                BindingTransferDiagnostics.skipReason(true, true, false, true));
        assertEquals(CONNECTION_NOT_ACCEPTING_MESSAGES,
                BindingTransferDiagnostics.skipReason(true, true, false, false));
        assertNull(BindingTransferDiagnostics.skipReason(true, true, true, false));
        // The diagnostic flag adds no new guard: the original condition was accepting messages.
        assertNull(BindingTransferDiagnostics.skipReason(true, true, true, true));
    }

    @Test
    void missingAndInvalidTargetsHaveDifferentReasonsWithoutChangingValidation() {
        assertEquals(TARGET_NOT_CONFIGURED, BindingTransferDiagnostics.targetProblem("", "25565"));
        assertEquals(TARGET_NOT_CONFIGURED, BindingTransferDiagnostics.targetProblem("proxy.example", " "));
        for (String port : new String[]{"bad", "0", "65536", "-1", " 25565"}) {
            assertEquals(TARGET_INVALID, BindingTransferDiagnostics.targetProblem("proxy.example", port));
        }
        assertNull(BindingTransferDiagnostics.targetProblem(" proxy.example ", "25565"));
        assertNull(BindingTransferDiagnostics.targetProblem("proxy.example", "1"));
        assertNull(BindingTransferDiagnostics.targetProblem("proxy.example", "65535"));
    }

    @ParameterizedTest
    @EnumSource(BindingTransferDiagnostics.Reason.class)
    void eachSkippedBranchReportsNameAndReasonInOneLine(BindingTransferDiagnostics.Reason reason) {
        Logger log = mock(Logger.class);
        BindingTransferDiagnostics.skipped(log, "TestUser", reason);
        verify(log).info("Binding transfer skipped: name={} reason={}", "TestUser", reason);
        verifyNoMoreInteractions(log);
    }
}
