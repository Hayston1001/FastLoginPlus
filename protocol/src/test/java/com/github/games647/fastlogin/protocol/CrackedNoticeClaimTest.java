/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrackedNoticeClaimTest {
    private final UUID id = UUID.randomUUID();
    private final UUID nonce = UUID.randomUUID();
    private final CrackedNoticeClaim notice = new CrackedNoticeClaim(true, "TestUser", id, nonce);

    private IdentityClaim identity(IdentityClaim.Decision decision, boolean marked) {
        return new IdentityClaim(decision, "TestUser", id,
                decision == IdentityClaim.Decision.PREMIUM_VERIFIED ? id : null,
                new IdentityClaim.Facts(false, marked, IdentityClaim.Consent.NO, false, false, false));
    }

    @Test
    void roundTripBindsAcknowledgementToNameUuidAndNonce() {
        CrackedNoticeClaim parsed = CrackedNoticeClaim.parse(Collections.singletonList(notice.encode()), "TestUser", id);
        assertEquals(notice.encode(), parsed.encode());
        assertTrue(parsed.matches(notice.acknowledgement("")));
        assertFalse(parsed.matches(new CrackedNoticeClaim(true, "TestUser", id, UUID.randomUUID()).acknowledgement("")));
        assertFalse(parsed.matches(new CrackedNoticeClaim(true, "OtherUser", id, nonce).acknowledgement("")));
        assertFalse(parsed.matches(new CrackedNoticeClaim(true, "TestUser", UUID.randomUUID(), nonce).acknowledgement("")));
    }

    @Test
    void missingIsOptionalAndFalseOrUnverifiedObservationAloneDoesNotCreateWork() {
        assertNull(CrackedNoticeClaim.parse(Collections.emptyList(), "TestUser", id));
        assertTrue(notice.accepts(identity(IdentityClaim.Decision.OFFLINE, true)));
        assertFalse(notice.accepts(identity(IdentityClaim.Decision.OFFLINE, false)));
        assertFalse(notice.accepts(identity(IdentityClaim.Decision.PREMIUM_VERIFIED, true)));
        assertFalse(new CrackedNoticeClaim(false, "TestUser", id, nonce)
                .accepts(identity(IdentityClaim.Decision.OFFLINE, true)));
    }

    @Test
    void malformedDuplicateUnknownVersionAndWrongConnectionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CrackedNoticeClaim.parse(
                Arrays.asList(notice.encode(), notice.encode()), "TestUser", id));
        for (String value : Arrays.asList("", "2:Y:TestUser:" + id + ":" + nonce,
                "1:X:TestUser:" + id + ":" + nonce, notice.encode() + ":extra", "1:Y:TestUser:1-1-1-1-1:" + nonce)) {
            assertThrows(IllegalArgumentException.class, () ->
                    CrackedNoticeClaim.parse(Collections.singletonList(value), "TestUser", id));
        }
        assertThrows(IllegalArgumentException.class, () -> CrackedNoticeClaim.parse(
                Collections.singletonList(notice.encode()), "OtherUser", id));
        assertThrows(IllegalArgumentException.class, () -> CrackedNoticeClaim.parse(
                Collections.singletonList(notice.encode()), "TestUser", UUID.randomUUID()));
    }

    @Test
    void independentPropertyLeavesLegacyIdentityEncodingUnchanged() {
        IdentityClaim claim = identity(IdentityClaim.Decision.OFFLINE, true);
        assertNotEquals(IdentityClaim.PROPERTY_NAME, CrackedNoticeClaim.PROPERTY_NAME);
        assertEquals(12, claim.encode().split(":", -1).length);
        assertEquals(claim.encode(), IdentityClaim.parseExactlyOne(
                Collections.singletonList(claim.encode()), "TestUser", id).encode());
    }
}
