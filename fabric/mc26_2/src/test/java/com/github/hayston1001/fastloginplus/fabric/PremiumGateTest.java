/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
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
    void passwordAccountsWithoutClaimableEntriesKeepPasswordLoginRegardlessOfConsent() {
        for (AccountState state : new AccountState[]{AccountState.PASSWORD, AccountState.BIND_PENDING}) {
            for (Consent consent : Consent.values()) {
                assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                        consent, new AccountState.Snapshot(state, null), PINNED, false, true));
            }
        }
    }

    @Test
    void liveClaimWithRecordedConsentConvertsTheAccount() {
        for (AccountState state : new AccountState[]{AccountState.PASSWORD, AccountState.BIND_PENDING}) {
            assertEquals(Action.CLAIM_AND_LOGIN, PremiumGate.decideVerified(
                    Consent.YES, new AccountState.Snapshot(state, null), PINNED, true, true));
        }
    }

    @Test
    void liveClaimWithoutRecordedConsentKeepsThePasswordLock() {
        for (Consent consent : new Consent[]{Consent.NO, Consent.UNKNOWN}) {
            for (AccountState state : new AccountState[]{AccountState.PASSWORD, AccountState.BIND_PENDING}) {
                assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                        consent, new AccountState.Snapshot(state, null), PINNED, true, true));
            }
        }
    }

    @Test
    void convertedAccountsOnlyLogInWithoutPassword() {
        for (Consent consent : Consent.values()) {
            assertEquals(Action.FREE_LOGIN, PremiumGate.decideVerified(
                    consent, CONVERTED, PINNED, false, true));
            // rule 8: a different verified identity never gets the converted account
            assertEquals(Action.REJECT, PremiumGate.decideVerified(
                    consent, CONVERTED, OTHER, false, true));
        }
    }

    @Test
    void conflictedAccountsStillRejectEveryIdentity() {
        AccountState.Snapshot conflict = new AccountState.Snapshot(AccountState.CONFLICT, null);
        for (Consent consent : Consent.values()) {
            assertEquals(Action.REJECT, PremiumGate.decideVerified(consent, conflict, PINNED, false, true));
            assertEquals(Action.REJECT, PremiumGate.decideVerified(consent, conflict, PINNED, true, false));
        }
        assertEquals(Action.REJECT, PremiumGate.decideUnverified(conflict));
    }

    @Test
    void autoLoginOffKeepsThePasswordFlow() {
        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                Consent.YES, PASSWORD_ACCOUNT, PINNED, true, false));
    }

    @Test
    void policyOffMatrixPreservesBoundOwnershipAndNeverClaimsOtherAccounts() {
        for (Consent consent : Consent.values()) {
            for (boolean autoRegister : new boolean[]{false, true}) {
                for (boolean claimMatches : new boolean[]{false, true}) {
                    assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                            consent, CONVERTED, PINNED, claimMatches, false, autoRegister));
                    assertEquals(Action.FREE_LOGIN, PremiumGate.decideVerified(
                            consent, CONVERTED, PINNED, claimMatches, true, autoRegister));
                    var disabled = new com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim(
                            com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Decision.PREMIUM_VERIFIED,
                            "TestUser", PINNED, PINNED,
                            new com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Facts(
                                    false, false, consent, false, autoRegister, false));
                    assertEquals("premium-policy-disabled", EasyAuth344Bridge.passwordLockMessage(
                            disabled, CONVERTED));
                    assertEquals(Action.REJECT, PremiumGate.decideVerified(
                            consent, CONVERTED, OTHER, claimMatches, false, autoRegister));
                    assertEquals(Action.REJECT, PremiumGate.decideVerified(
                            consent, CONVERTED, null, claimMatches, false, autoRegister));
                    assertEquals(Action.REJECT, PremiumGate.decideUnverified(CONVERTED));
                    for (AccountState state : new AccountState[]{AccountState.PASSWORD,
                            AccountState.BIND_PENDING, AccountState.OPEN}) {
                        AccountState.Snapshot account = new AccountState.Snapshot(state, null);
                        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideVerified(
                                consent, account, PINNED, claimMatches, false, autoRegister));
                        // No policy parameter exists on the unverified path: it cannot grant access.
                        assertEquals(Action.PASSWORD_LOCK, PremiumGate.decideUnverified(account));
                    }
                }
            }
        }
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
