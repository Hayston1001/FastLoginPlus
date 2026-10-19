/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;

import java.util.UUID;

/**
 * The premium gate: what FastLoginPlus does with one connection, from the
 * identity facts and the local account state alone. Kept pure so every branch
 * is unit-testable.
 */
final class PremiumGate {

    /** What the premium path does with this connection. */
    enum Action {
        /** Refuse the connection (account ownership conflict). */
        REJECT,
        /** Leave the player behind the EasyAuth password (or register) prompt. */
        PASSWORD_LOCK,
        /** Convert the account and log the verified identity in without a password. */
        CLAIM_AND_LOGIN,
        /** Log the verified identity in without a password. */
        FREE_LOGIN
    }

    private PremiumGate() {
    }

    /**
     * Converts a password account only with recorded proxy consent and a verified
     * identity. Its password is retained but dormant while premium-bound. Unknown
     * accounts follow the registration policy; bound accounts require their UUID.
     *
     * @param consent the name's upgrade consent as the proxy recorded it
     * @param state the local account state
     * @param verifiedUuid the Mojang UUID this connection was verified as
     * @param autoLogin the proxy's autoLogin policy value
     * @return the action to apply
     */
    static Action decideVerified(IdentityClaim.Consent consent, AccountState.Snapshot state,
                                 UUID verifiedUuid, boolean autoLogin) {
        return decideVerified(consent, state, verifiedUuid, autoLogin, true);
    }

    /**
     * Full form of {@link #decideVerified(IdentityClaim.Consent,
     * AccountState.Snapshot, UUID, boolean)} with the auto-register
     * policy.
     *
     * @param consent the name's upgrade consent as the proxy recorded it
     * @param state the local account state
     * @param verifiedUuid the Mojang UUID this connection was verified as
     * @param autoLogin the proxy's autoLogin policy value
     * @param autoRegisterUnknown the proxy's auto-register-unknown policy value
     * @return the action to apply
     */
    static Action decideVerified(IdentityClaim.Consent consent, AccountState.Snapshot state,
                                 UUID verifiedUuid, boolean autoLogin,
                                 boolean autoRegisterUnknown) {
        if (state.state() == AccountState.CONFLICT) {
            return Action.REJECT;
        }
        if (state.state() == AccountState.BOUND
                && (verifiedUuid == null || !verifiedUuid.equals(state.uuid()))) {
            return Action.REJECT;
        }
        if (!autoLogin) {
            // Policy disables all passwordless login, including an already bound account.
            return Action.PASSWORD_LOCK;
        }
        if (state.state() == AccountState.BOUND) {
            return Action.FREE_LOGIN;
        }
        if (state.state() == AccountState.OPEN) {
            // no account, no ownership question. On (default) the unclaimed
            // entry is converted and the player logged in; with
            // auto-register-unknown off the player gets the
            // register dialog instead of an automatic account
            return autoRegisterUnknown ? Action.FREE_LOGIN : Action.PASSWORD_LOCK;
        }
        if (state.state() == AccountState.PASSWORD && consent == IdentityClaim.Consent.YES) {
            return Action.CLAIM_AND_LOGIN;
        }
        // Without consent, the existing password remains the login method.
        return Action.PASSWORD_LOCK;
    }

    /**
     * Decides for an unverified (offline or same-name cracked) connection.
     * Converted accounts refuse it; everything else stays behind the password.
     *
     * @param state the local account state
     * @return the action to apply
     */
    static Action decideUnverified(AccountState.Snapshot state) {
        if (state.state() == AccountState.BOUND || state.state() == AccountState.CONFLICT) {
            return Action.REJECT;
        }
        return Action.PASSWORD_LOCK;
    }
}
