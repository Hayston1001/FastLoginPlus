/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityClaimTest {

    @Test
    void legacySignatureAuthenticatesExactBytesWithoutDisclosingTheProxyId() throws Exception {
        UUID proxy = UUID.randomUUID();
        String value = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "TestUser", MOJANG, MOJANG).encode();
        String signature = IdentityClaim.sign(value, proxy);
        assertTrue(signature.matches("[A-Za-z0-9_-]{43}"));
        assertFalse(value.contains(proxy.toString()));
        assertTrue(IdentityClaim.verifySignature(value, signature, proxy));
        assertFalse(IdentityClaim.verifySignature(value, signature, UUID.randomUUID()));
        assertFalse(IdentityClaim.verifySignature(value + " ", signature, proxy));
        assertFalse(IdentityClaim.verifySignature(value, null, proxy));
        assertFalse(IdentityClaim.verifySignature(value, "", proxy));
        assertFalse(IdentityClaim.verifySignature(value, "%%%", proxy));
        assertFalse(IdentityClaim.verifySignature(value, signature + "=", proxy));
        String plaintext = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "TestUser", MOJANG, MOJANG, IdentityClaim.Facts.defaults(), proxy).encode();
        assertFalse(IdentityClaim.verifySignature(plaintext, null, proxy));
        assertFalse(IdentityClaim.verifySignature(plaintext, signature, proxy));

        // Pin the interoperable key representation, domain separator and exact signed bytes.
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(proxy.toString().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        assertEquals(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                ("fastloginplus:flp_identity:v1\0" + value).getBytes(StandardCharsets.UTF_8))), signature);
    }

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
    void fabricPolicyRejectsUnresolvedAndArbitrarilyRewrittenPremiumIdentity() {
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
    }

    @Test
    void fabricPolicyRoundTripsVerifiedPremiumIdentity() {
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "Player_1", MOJANG, MOJANG);

        IdentityClaim parsed = IdentityClaim.parseForFabric(
                Collections.singletonList(claim.encode()), "Player_1", MOJANG);

        assertEquals(IdentityClaim.Decision.PREMIUM_VERIFIED, parsed.getDecision());
        assertEquals(MOJANG, parsed.getForwardedUuid());
        assertEquals(MOJANG, parsed.getVerifiedMojangUuid());
        assertEquals(claim.encode(), parsed.encode());
    }

    @Test
    void fabricPolicyRoundTripsPremiumIdentityWithOfflineUuid() {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Player_1".getBytes(StandardCharsets.UTF_8));
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "Player_1", offline, MOJANG);

        IdentityClaim parsed = IdentityClaim.parseForFabric(
                Collections.singletonList(claim.encode()), "Player_1", offline);

        assertEquals(IdentityClaim.Decision.PREMIUM_VERIFIED, parsed.getDecision());
        assertEquals(offline, parsed.getForwardedUuid());
        assertEquals(MOJANG, parsed.getVerifiedMojangUuid());
        assertEquals(claim.encode(), parsed.encode());
    }

    @Test
    void fabricPolicyRejectsOfflineDowngradeForDifferentNameCase() {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:player_1".getBytes(StandardCharsets.UTF_8));
        for (IdentityClaim.Decision decision : Arrays.asList(
                IdentityClaim.Decision.PREMIUM_VERIFIED, IdentityClaim.Decision.OFFLINE)) {
            IdentityClaim claim = new IdentityClaim(decision, "Player_1", offline,
                    decision == IdentityClaim.Decision.PREMIUM_VERIFIED ? MOJANG : null);
            assertThrows(IllegalArgumentException.class, () -> IdentityClaim.parseForFabric(
                    Collections.singletonList(claim.encode()), "Player_1", offline));
        }
    }

    @Test
    void fabricPolicyRoundTripsOfflineIdentity() {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Player_1".getBytes(StandardCharsets.UTF_8));
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.OFFLINE, "Player_1", offline, null);

        IdentityClaim parsed = IdentityClaim.parseForFabric(
                Collections.singletonList(claim.encode()), "Player_1", offline);

        assertEquals(IdentityClaim.Decision.OFFLINE, parsed.getDecision());
        assertEquals(offline, parsed.getForwardedUuid());
        assertEquals(null, parsed.getVerifiedMojangUuid());
        assertEquals(claim.encode(), parsed.encode());
    }
}
