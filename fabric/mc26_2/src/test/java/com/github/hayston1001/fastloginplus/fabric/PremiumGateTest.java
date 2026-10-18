/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.PremiumGate.Action;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Consent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PremiumGateTest {
    private static final UUID VERIFIED = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @ParameterizedTest
    @EnumSource(Consent.class)
    void verifiedStateAndPolicyMatrix(Consent consent) {
        for (boolean autoLogin : new boolean[]{false, true}) {
            for (boolean autoRegister : new boolean[]{false, true}) {
                for (AccountState state : AccountState.values()) {
                    var account = new AccountState.Snapshot(state, state == AccountState.BOUND ? VERIFIED : null);
                    Action expected = switch (state) {
                        case CONFLICT -> Action.REJECT;
                        case BOUND -> autoLogin ? Action.FREE_LOGIN : Action.PASSWORD_LOCK;
                        case OPEN -> autoLogin && autoRegister ? Action.FREE_LOGIN : Action.PASSWORD_LOCK;
                        case PASSWORD -> autoLogin && consent == Consent.YES
                                ? Action.CLAIM_AND_LOGIN : Action.PASSWORD_LOCK;
                    };
                    assertEquals(expected, PremiumGate.decideVerified(
                            consent, account, VERIFIED, autoLogin, autoRegister),
                            state + " " + consent + " " + autoLogin + " " + autoRegister);
                    if (autoRegister) {
                        assertEquals(expected, PremiumGate.decideVerified(consent, account, VERIFIED, autoLogin));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Consent.class)
    void boundUuidMismatchRejectsEvenWhenAutoLoginIsDisabled(Consent consent) {
        var bound = new AccountState.Snapshot(AccountState.BOUND, VERIFIED);
        for (boolean autoLogin : new boolean[]{false, true}) {
            for (boolean autoRegister : new boolean[]{false, true}) {
                assertEquals(Action.REJECT, PremiumGate.decideVerified(
                        consent, bound, OTHER, autoLogin, autoRegister));
                assertEquals(Action.REJECT, PremiumGate.decideVerified(
                        consent, bound, null, autoLogin, autoRegister));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(AccountState.class)
    void unverifiedBoundAndConflictRejectButOtherAccountsStayLocked(AccountState state) {
        assertEquals(state == AccountState.BOUND || state == AccountState.CONFLICT
                        ? Action.REJECT : Action.PASSWORD_LOCK,
                PremiumGate.decideUnverified(new AccountState.Snapshot(state,
                        state == AccountState.BOUND ? VERIFIED : null)));
    }
}
