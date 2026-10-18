/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.protocol.IdentityClaim.Decision;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityDecisionPolicyTest {

    private static final UUID VERIFIED = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID OTHER = UUID.fromString("abcdefab-cdef-cdef-cdef-abcdefabcdef");

    @Test
    void missingSessionAndUndecidedSessionRemainUnresolved() {
        assertEquals(Decision.UNDECIDED, decide(null, false, false, true, "Player_1", VERIFIED).getDecision());
        VelocityLoginSession session = new VelocityLoginSession("Player_1", false, null);
        assertEquals(Decision.UNDECIDED, decide(session, false, false, true, "Player_1", VERIFIED).getDecision());
    }

    @Test
    void offlineRequiresExplicitDecisionAndNonBedrockConnection() {
        VelocityLoginSession session = new VelocityLoginSession("Player_1", false, null);
        session.setIdentityDecision(Decision.OFFLINE);
        UUID offline = UUIDAdapter.generateOfflineId("Player_1");
        assertEquals(Decision.OFFLINE, decide(session, false, false, true, "Player_1", offline).getDecision());
        assertEquals(Decision.OFFLINE, decide(session, false, false, false, "Player_1", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, false, true, true, "Player_1", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, false, false, true, "Other", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, false, false, true, "Player_1", VERIFIED).getDecision());
    }

    @Test
    void onlineIdentityMustMatchFinalForwardedNameAndUuid() {
        VelocityLoginSession session = new VelocityLoginSession("Player_1", true, null);
        session.setVerifiedUsername("Player_1");
        session.setUuid(VERIFIED);

        assertEquals(Decision.UNDECIDED, decide(session, true, false, true, "Other", VERIFIED).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, true, false, true, "Player_1", OTHER).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, true, true, true, "Player_1", VERIFIED).getDecision());

        IdentityClaim claim = decide(session, true, false, true, "Player_1", VERIFIED);
        assertEquals(Decision.PREMIUM_VERIFIED, claim.getDecision());
        assertEquals(VERIFIED, claim.getVerifiedMojangUuid());
        assertEquals(Decision.PREMIUM_VERIFIED, session.getIdentityDecision());
    }

    @Test
    void disabledPremiumUuidAllowsOnlyTheNameDerivedOfflineDowngrade() {
        VelocityLoginSession session = verifiedSession();
        UUID offline = UUIDAdapter.generateOfflineId("Player_1");

        IdentityClaim claim = decide(session, true, false, false, "Player_1", offline);

        assertEquals(Decision.PREMIUM_VERIFIED, claim.getDecision());
        assertEquals(offline, claim.getForwardedUuid());
        assertEquals(VERIFIED, claim.getVerifiedMojangUuid());
        assertEquals(Decision.PREMIUM_VERIFIED, session.getIdentityDecision());
        assertEquals(VERIFIED, session.getUuid());
        assertEquals(Decision.PREMIUM_VERIFIED,
                decide(session, true, false, false, "Player_1", VERIFIED).getDecision());
    }

    @Test
    void disabledPremiumUuidStillRejectsArbitraryUuidRewrites() {
        VelocityLoginSession session = verifiedSession();

        IdentityClaim claim = decide(session, true, false, false, "Player_1", OTHER);

        assertEquals(Decision.UNDECIDED, claim.getDecision());
        assertEquals(null, claim.getVerifiedMojangUuid());
        assertEquals(Decision.UNDECIDED, session.getIdentityDecision());
    }

    @Test
    void enabledPremiumUuidRejectsOfflineDowngrade() {
        VelocityLoginSession session = verifiedSession();
        UUID offline = UUIDAdapter.generateOfflineId("Player_1");

        IdentityClaim claim = decide(session, true, false, true, "Player_1", offline);

        assertEquals(Decision.UNDECIDED, claim.getDecision());
        assertEquals(null, claim.getVerifiedMojangUuid());
        assertEquals(Decision.UNDECIDED, session.getIdentityDecision());
    }

    @Test
    void offlineDowngradeStillRequiresVerifiedMatchingJavaSession() {
        VelocityLoginSession session = verifiedSession();
        UUID offline = UUIDAdapter.generateOfflineId("Player_1");
        assertEquals(Decision.UNDECIDED, decide(null, true, false, false, "Player_1", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, false, false, false, "Player_1", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, true, true, false, "Player_1", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, true, false, false, "Other",
                UUIDAdapter.generateOfflineId("Other")).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, true, false, false, "Player_1",
                UUIDAdapter.generateOfflineId("player_1")).getDecision());
        session.setUuid(null);
        assertEquals(Decision.UNDECIDED, decide(session, true, false, false, "Player_1", offline).getDecision());
    }

    private static VelocityLoginSession verifiedSession() {
        VelocityLoginSession session = new VelocityLoginSession("Player_1", true, null);
        session.setVerifiedUsername("Player_1");
        session.setUuid(VERIFIED);
        return session;
    }

    private static IdentityClaim decide(VelocityLoginSession session, boolean online, boolean bedrock,
                                        boolean premiumUuid, String name, UUID uuid) {
        return IdentityDecisionPolicy.decide(session, online, bedrock, premiumUuid, name, uuid);
    }
}
