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
        assertEquals(Decision.UNDECIDED, decide(null, false, false, "Player_1", VERIFIED).getDecision());
        VelocityLoginSession session = new VelocityLoginSession("Player_1", false, null);
        assertEquals(Decision.UNDECIDED, decide(session, false, false, "Player_1", VERIFIED).getDecision());
    }

    @Test
    void offlineRequiresExplicitDecisionAndNonBedrockConnection() {
        VelocityLoginSession session = new VelocityLoginSession("Player_1", false, null);
        session.setIdentityDecision(Decision.OFFLINE);
        UUID offline = UUIDAdapter.generateOfflineId("Player_1");
        assertEquals(Decision.OFFLINE, decide(session, false, false, "Player_1", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, false, true, "Player_1", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, false, false, "Other", offline).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, false, false, "Player_1", VERIFIED).getDecision());
    }

    @Test
    void onlineIdentityMustMatchFinalForwardedNameAndUuid() {
        VelocityLoginSession session = new VelocityLoginSession("Player_1", true, null);
        session.setVerifiedUsername("Player_1");
        session.setUuid(VERIFIED);

        assertEquals(Decision.UNDECIDED, decide(session, true, false, "Other", VERIFIED).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, true, false, "Player_1", OTHER).getDecision());
        assertEquals(Decision.UNDECIDED, decide(session, true, true, "Player_1", VERIFIED).getDecision());

        IdentityClaim claim = decide(session, true, false, "Player_1", VERIFIED);
        assertEquals(Decision.PREMIUM_VERIFIED, claim.getDecision());
        assertEquals(VERIFIED, claim.getVerifiedMojangUuid());
    }

    private static IdentityClaim decide(VelocityLoginSession session, boolean online, boolean bedrock,
                                        String name, UUID uuid) {
        return IdentityDecisionPolicy.decide(session, online, bedrock, name, uuid);
    }
}
