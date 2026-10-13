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
package com.github.games647.fastlogin.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityClaimTest {

    private static final UUID FORWARDED = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID MOJANG = UUID.fromString("abcdefab-cdef-cdef-cdef-abcdefabcdef");

    @Test
    void roundTripsEveryDecision() {
        for (IdentityClaim.Decision decision : IdentityClaim.Decision.values()) {
            UUID verified = decision == IdentityClaim.Decision.PREMIUM_VERIFIED ? MOJANG : null;
            IdentityClaim claim = new IdentityClaim(decision, "Player_1", FORWARDED, verified);
            IdentityClaim parsed = IdentityClaim.parseExactlyOne(
                    Collections.singletonList(claim.encode()), "Player_1", FORWARDED);
            assertEquals(decision, parsed.getDecision());
            assertEquals(verified, parsed.getVerifiedMojangUuid());
        }
    }

    @Test
    void rejectsMissingDuplicateAndMismatchedClaims() {
        String encoded = new IdentityClaim(IdentityClaim.Decision.OFFLINE,
                "Player_1", FORWARDED, null).encode();
        assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseExactlyOne(
                Collections.emptyList(), "Player_1", FORWARDED));
        assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseExactlyOne(
                Arrays.asList(encoded, encoded), "Player_1", FORWARDED));
        assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseExactlyOne(
                Collections.singletonList(encoded), "Other", FORWARDED));
        assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseExactlyOne(
                Collections.singletonList(encoded), "Player_1", MOJANG));
    }

    @Test
    void roundTripsIdentityFacts() {
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.OFFLINE,
                "Player_1", FORWARDED, null, new IdentityClaim.Facts(
                        true, true, IdentityClaim.Consent.NO, false, true, true));

        IdentityClaim parsed = IdentityClaim.parseExactlyOne(
                Collections.singletonList(claim.encode()), "Player_1", FORWARDED);

        assertEquals(true, parsed.isNewPlayer());
        assertEquals(true, parsed.isSameNameCracked());
        assertEquals(IdentityClaim.Consent.NO, parsed.getConsent());
        assertEquals(false, parsed.isAutoLogin());
        assertEquals(true, parsed.isAutoRegisterUnknown());
        assertEquals(true, parsed.isOfflineWhitelist());
    }

    @Test
    void shortShapeFromOlderProxyLeavesFactsUnknown() {
        // five fields: the shape a proxy predating the facts sends
        String value = "1:OFFLINE:Player_1:" + FORWARDED + ":-";

        IdentityClaim parsed = IdentityClaim.parseExactlyOne(
                Collections.singletonList(value), "Player_1", FORWARDED);

        assertEquals(false, parsed.isNewPlayer());
        assertEquals(false, parsed.isSameNameCracked());
        assertEquals(IdentityClaim.Consent.UNKNOWN, parsed.getConsent());
        assertEquals(true, parsed.isAutoLogin());
        assertEquals(false, parsed.isAutoRegisterUnknown());
        assertEquals(false, parsed.isOfflineWhitelist());
    }

    @Test
    void roundTripsTheProxyIdToken() {
        UUID proxyId = UUID.fromString("c1a0d5d2-9e5b-4d0a-9c1e-2b4f6a8c0d1e");
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "Player_1", FORWARDED, MOJANG, IdentityClaim.Facts.defaults(), proxyId);

        IdentityClaim parsed = IdentityClaim.parseExactlyOne(
                Collections.singletonList(claim.encode()), "Player_1", FORWARDED);

        assertEquals(proxyId, parsed.getProxyId());
    }

    @Test
    void rejectsMalformedWireValues() {
        String prefix = "1:PREMIUM_VERIFIED:Player_1:" + FORWARDED + ":";
        for (String value : Arrays.asList("2:OFFLINE:Player_1:" + FORWARDED + ":-",
                "1:UNKNOWN:Player_1:" + FORWARDED + ":-",
                "1:OFFLINE:Player_1:not-a-uuid:-",
                "1:OFFLINE:Player_1:" + FORWARDED + ":" + MOJANG,
                prefix + "-", prefix + "not-a-uuid",
                "1:OFFLINE:Player_1:" + FORWARDED + ":-:extra")) {
            assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseExactlyOne(
                    Collections.singletonList(value), "Player_1", FORWARDED));
        }
    }

    @Test
    void fabricPolicyRejectsUnresolvedAndRewrittenPremiumIdentity() {
        String undecided = new IdentityClaim(IdentityClaim.Decision.UNDECIDED,
                "Player_1", FORWARDED, null).encode();
        String rewritten = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "Player_1", FORWARDED, MOJANG).encode();
        assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseForFabric(
                Collections.singletonList(undecided), "Player_1", FORWARDED));
        assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseForFabric(
                Collections.singletonList(rewritten), "Player_1", FORWARDED));
        assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseForFabric(
                Collections.singletonList(new IdentityClaim(IdentityClaim.Decision.OFFLINE,
                        "Player_1", FORWARDED, null).encode()), "Player_1", FORWARDED));
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Player_1".getBytes(StandardCharsets.UTF_8));
        assertEquals(IdentityClaim.Decision.OFFLINE, IdentityClaim.parseForFabric(
                Collections.singletonList(new IdentityClaim(IdentityClaim.Decision.OFFLINE,
                        "Player_1", offline, null).encode()), "Player_1", offline).getDecision());
    }
}
