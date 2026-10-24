/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FabricLoginFailureTest {

    @Test
    void policyFailureKeepsItsSpecificClientKey() {
        var failure = FabricMessages.failure("bedrock-unsupported");

        assertEquals(FabricLoginFailure.Kind.POLICY_DENIED, FabricLoginFailure.classify(failure));
        assertEquals("bedrock-unsupported", FabricLoginFailure.messageKey(failure));
    }

    @Test
    void ownershipConflictIsNotReportedAsStorageFailure() {
        var failure = FabricMessages.failure("ownership-conflict");
        Logger logger = mock(Logger.class);

        assertEquals(FabricLoginFailure.Kind.POLICY_DENIED, FabricLoginFailure.classify(failure));
        assertEquals("ownership-conflict", FabricLoginFailure.messageKey(failure));
        FabricLoginFailure.log(logger, "Name", failure);
        verify(logger).warn("Denied login for {}: {}", "Name", "ownership-conflict");
    }

    @Test
    void wrappedPolicyFailureStillWinsAndKeepsItsKey() {
        var failure = new AuthUnavailableException("unexpected wrapper",
                FabricMessages.failure("ownership-conflict"));
        Logger logger = mock(Logger.class);

        assertEquals(FabricLoginFailure.Kind.POLICY_DENIED, FabricLoginFailure.classify(failure));
        assertEquals("ownership-conflict", FabricLoginFailure.messageKey(failure));
        FabricLoginFailure.log(logger, "Name", failure);
        verify(logger).warn("Denied login for {}: {}", "Name", "ownership-conflict");
    }

    @Test
    void malformedExpiredAndReplayedClaimsAreInvalidClaims() {
        Logger logger = mock(Logger.class);
        var malformed = new BedrockClaimException("Invalid signed Bedrock claim",
                new IllegalArgumentException("Unbound or expired Bedrock property"));
        var replayed = new BedrockClaimException("Replayed Bedrock claim");

        for (Throwable failure : new Throwable[]{
                malformed, replayed, new IllegalArgumentException("Unbound or expired Bedrock property")}) {
            assertEquals(FabricLoginFailure.Kind.INVALID_CLAIM, FabricLoginFailure.classify(failure));
            assertEquals("invalid-identity", FabricLoginFailure.messageKey(failure));
        }
        FabricLoginFailure.log(logger, "Name", malformed);
        verify(logger).warn("Rejected identity claim for {}: {}", "Name", "Invalid signed Bedrock claim");
    }

    @Test
    void storageAndTimeoutFailuresAreAuthUnavailable() {
        Logger logger = mock(Logger.class);
        var busy = new AuthUnavailableException("EasyAuth Bedrock account check failed",
                new SQLException("SQLITE_BUSY: database is locked"));
        var closed = new AuthUnavailableException("EasyAuth Bedrock account check failed",
                new IllegalStateException("EasyAuth database is closed"));
        var timeout = new AuthUnavailableException("EasyAuth Bedrock account check timed out");

        for (Throwable failure : new Throwable[]{busy, closed, timeout}) {
            assertEquals(FabricLoginFailure.Kind.AUTH_UNAVAILABLE, FabricLoginFailure.classify(failure));
            assertEquals("auth-unavailable", FabricLoginFailure.messageKey(failure));
        }
        FabricLoginFailure.log(logger, "Name", busy);
        verify(logger).warn(eq("Could not verify the EasyAuth account of {}: {}"), eq("Name"),
                contains("SQLITE_BUSY: database is locked"));
    }

    @Test
    void unexpectedRuntimeFailureIsInternalAndLoggedWithStack() {
        var failure = new NullPointerException("unexpected");
        Logger logger = mock(Logger.class);

        assertEquals(FabricLoginFailure.Kind.INTERNAL_ERROR, FabricLoginFailure.classify(failure));
        assertEquals("auth-unavailable", FabricLoginFailure.messageKey(failure));
        FabricLoginFailure.log(logger, "Name", failure);
        verify(logger).error("Unexpected failure while verifying Name", failure);
    }
}
