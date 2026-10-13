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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState;
import com.github.games647.fastlogin.core.shared.LoginMethodDecider.Outcome;
import com.github.games647.fastlogin.protocol.IdentityClaim.Consent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the D-03 decision table row by row, plus the two confirmed extensions:
 * Q-B's three-state consent and R-7's auth-plugin premium accounts.
 */
class LoginMethodDeciderTest {

    @Test
    void verifiedPremiumWithoutAccountAutoRegisters() {
        assertEquals(Outcome.AUTO_REGISTER_LOGIN,
                LoginMethodDecider.decide(true, AccountState.NONE, Consent.UNKNOWN));
        assertEquals(Outcome.AUTO_REGISTER_LOGIN,
                LoginMethodDecider.decide(true, AccountState.NONE, Consent.YES));
    }

    @Test
    void verifiedPremiumWithConsentLogsIn() {
        assertEquals(Outcome.AUTO_LOGIN,
                LoginMethodDecider.decide(true, AccountState.PASSWORD_ACCOUNT, Consent.YES));
    }

    @Test
    void verifiedPremiumOntoPasswordAccountIsRejected() {
        // rule 7: a password lock would be a dead end for the verified owner
        assertEquals(Outcome.REJECT,
                LoginMethodDecider.decide(true, AccountState.PASSWORD_ACCOUNT, Consent.NO));
    }

    @Test
    void unverifiedOntoPasswordAccountStaysBehindThePassword() {
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(false, AccountState.PASSWORD_ACCOUNT, Consent.NO));
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(false, AccountState.PASSWORD_ACCOUNT, Consent.UNKNOWN));
    }

    @Test
    void unverifiedOntoConsentedPremiumAccountIsRejected() {
        // rule 8, the symmetric row
        assertEquals(Outcome.REJECT,
                LoginMethodDecider.decide(false, AccountState.PASSWORD_ACCOUNT, Consent.YES));
    }

    @Test
    void unverifiedNewNameGoesToRegistration() {
        assertEquals(Outcome.GUIDE_REGISTER,
                LoginMethodDecider.decide(false, AccountState.NONE, Consent.UNKNOWN));
    }

    @Test
    void unknownConsentNeverUnlocksAnything() {
        // Q-B three-state: facts that never arrived keep the password lock
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(true, AccountState.PASSWORD_ACCOUNT, Consent.UNKNOWN));
    }

    @Test
    void unreadableAccountFailsClosedToThePasswordLock() {
        // rule 1: never passwordless on an account state that could not be read
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(true, AccountState.READ_FAILURE, Consent.YES));
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(false, AccountState.READ_FAILURE, Consent.NO));
    }

    @Test
    void autoLoginOffKeepsEveryoneOnThePasswordFlow() {
        // D-06 #1: no premium handling at all when the proxy policy is off
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(true, AccountState.PASSWORD_ACCOUNT, Consent.YES, false));
        assertEquals(Outcome.GUIDE_REGISTER,
                LoginMethodDecider.decide(true, AccountState.NONE, Consent.UNKNOWN, false));
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(false, AccountState.PASSWORD_ACCOUNT, Consent.YES, false));
    }

    @Test
    void undeclaredFactsFromAnOldProxyStayConservative() {
        // a proxy that predates the facts declares nothing: the consent is
        // UNKNOWN, which never unlocks a password account (and a name without an
        // account has no ownership question either)
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(true, AccountState.PASSWORD_ACCOUNT, Consent.UNKNOWN));
        assertEquals(Outcome.AUTO_REGISTER_LOGIN,
                LoginMethodDecider.decide(true, AccountState.NONE, Consent.UNKNOWN));
        assertEquals(Outcome.PASSWORD,
                LoginMethodDecider.decide(false, AccountState.PASSWORD_ACCOUNT, Consent.UNKNOWN));
    }

    @Test
    void authPluginPremiumAccountCountsAsConsent() {
        // R-7: an account the auth plugin itself established as premium is
        // trusted as an upgrade consent
        assertEquals(Outcome.AUTO_LOGIN,
                LoginMethodDecider.decide(true, AccountState.PREMIUM_OWNED, Consent.UNKNOWN));
        assertEquals(Outcome.REJECT,
                LoginMethodDecider.decide(false, AccountState.PREMIUM_OWNED, Consent.UNKNOWN));
    }
}
