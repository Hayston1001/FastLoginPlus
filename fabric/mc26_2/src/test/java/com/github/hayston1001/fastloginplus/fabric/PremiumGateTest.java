/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.PremiumGate.Action;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Consent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PremiumGateTest {

    private static final UUID PINNED = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final AccountState.Snapshot PASSWORD_ACCOUNT =
            new AccountState.Snapshot(AccountState.PASSWORD, null);
    private static final AccountState.Snapshot CONVERTED =
            new AccountState.Snapshot(AccountState.BOUND, PINNED);
    private static final AccountState.Snapshot NO_ACCOUNT =
            new AccountState.Snapshot(AccountState.OPEN, null);

    @Test
    void newPremiumPlayerIsRegisteredAndLoggedIn() {
        // decision table row 1: no account means no ownership question (R-1 is
        // about accounts, not names), so a new player's NO consent is irrelevant
        assertEquals(Action.FREE_LOGIN, PremiumGate.decideVerified(
                Consent.NO, NO_ACCOUNT, PINNED, false, true));
        assertEquals(Action.FREE_LOGIN, PremiumGate.decideVerified(
                Consent.UNKNOWN, NO_ACCOUNT, PINNED, false, true));
    }

    @Test
    void newPremiumPlayerStaysOnTheRegisterFlowWhenAutoLoginIsOff() {
        // D-06 #1: autoLogin=false registers nothing
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                Consent.NO, NO_ACCOUNT, PINNED, false, false));
    }

    @Test
    void newPremiumPlayerStaysOnTheRegisterFlowWhenAutoRegisterUnknownIsOff() {
        // F-15 A: auto-register-unknown off keeps the player in the register
        // dialog instead of creating an account; on (the default) creates it
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                Consent.NO, NO_ACCOUNT, PINNED, false, true, false));
        assertEquals(Action.FREE_LOGIN, PremiumGate.decideVerified(
                Consent.NO, NO_ACCOUNT, PINNED, false, true, true));
    }

    @Test
    void expiredClaimWindowFallsBackToThePasswordFlow() {
        // the queued entry is gone after its window: consented, still a password
        // account -> no conversion, no rejection, the password stays usable
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                Consent.YES, PASSWORD_ACCOUNT, PINNED, false, true));
    }

    @Test
    void claimEntryLostToRestartFallsBackToThePasswordFlow() {
        // same shape after a backend restart dropped the in-memory entries
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                Consent.YES, PASSWORD_ACCOUNT, PINNED, false, true));
    }

    @Test
    void failedUpgradeLeavesThePasswordFlowUsable() {
        // the upgrade never finished (the account stayed a password account):
        // the owner retries /flp premium after logging in with the password
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                Consent.YES, PASSWORD_ACCOUNT, PINNED, false, true));
    }

    @Test
    void unconsentedPasswordAccountIsStillRefused() {
        // rule 7: the verified owner of someone else's password account gets a
        // clear refusal, not a password prompt they can never pass
        assertEquals(Action.REJECT, PremiumGate.decideVerified(
                Consent.NO, PASSWORD_ACCOUNT, PINNED, false, true));
        assertEquals(Action.REJECT, PremiumGate.decideVerified(
                Consent.UNKNOWN, PASSWORD_ACCOUNT, PINNED, false, true));
    }

    @Test
    void liveClaimConvertsTheAccount() {
        assertEquals(Action.CLAIM_AND_LOGIN, PremiumGate.decideVerified(
                Consent.YES, PASSWORD_ACCOUNT, PINNED, true, true));
    }

    @Test
    void convertedAccountsOnlyLogInWithoutPassword() {
        assertEquals(Action.FREE_LOGIN, PremiumGate.decideVerified(
                Consent.YES, CONVERTED, PINNED, false, true));
        // rule 8: a different verified identity never gets the converted account
        assertEquals(Action.REJECT, PremiumGate.decideVerified(
                Consent.YES, CONVERTED, OTHER, false, true));
    }

    @Test
    void autoLoginOffKeepsThePasswordFlow() {
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                Consent.YES, PASSWORD_ACCOUNT, PINNED, true, false));
    }

    @Test
    void unverifiedConnectionsStayBehindThePassword() {
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideUnverified(PASSWORD_ACCOUNT));
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideUnverified(
                new AccountState.Snapshot(AccountState.OPEN, null)));
        // a converted account refuses unverified identities (rule 8)
        assertEquals(Action.REJECT, PremiumGate.decideUnverified(CONVERTED));
    }
}
