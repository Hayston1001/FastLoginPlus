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
     * Decides for a Mojang-verified premium connection. A queued claim (live
     * entry whose pinned UUID matches) converts a password account during login
     * only with the proxy's recorded consent. A pending window never permits
     * conversion without that consent. Otherwise the password flow remains;
     * its authenticated holder can request binding with /flp premium afterward.
     * A name without an account has no ownership question
     * (rule 1 row 1): it is auto-registered and logged in — unless the
     * auto-register-unknown policy is off (F-15 A), which keeps the player in
     * the register dialog instead. Converted accounts only ever log in without
     * a password, so a mismatched identity is refused (rule 8).
     *
     * @param consent the name's upgrade consent as the proxy recorded it
     * @param state the local account state
     * @param verifiedUuid the Mojang UUID this connection was verified as
     * @param claimEntryMatches whether a live claim entry pins this verified UUID
     * @param autoLogin the proxy's autoLogin policy value
     * @return the action to apply
     */
    static Action decideVerified(IdentityClaim.Consent consent, AccountState.Snapshot state,
                                 UUID verifiedUuid, boolean claimEntryMatches, boolean autoLogin) {
        return decideVerified(consent, state, verifiedUuid, claimEntryMatches, autoLogin, true);
    }

    /**
     * Full form of {@link #decideVerified(IdentityClaim.Consent,
     * AccountState.Snapshot, UUID, boolean, boolean)} with the auto-register
     * policy (F-15 A).
     *
     * @param consent the name's upgrade consent as the proxy recorded it
     * @param state the local account state
     * @param verifiedUuid the Mojang UUID this connection was verified as
     * @param claimEntryMatches whether a live claim entry pins this verified UUID
     * @param autoLogin the proxy's autoLogin policy value
     * @param autoRegisterUnknown the proxy's auto-register-unknown policy value
     * @return the action to apply
     */
    static Action decideVerified(IdentityClaim.Consent consent, AccountState.Snapshot state,
                                 UUID verifiedUuid, boolean claimEntryMatches, boolean autoLogin,
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
            // decision table row 1: no account, no ownership question. On
            // (default) the unclaimed entry is converted and the player logged
            // in; with auto-register-unknown off (F-15 A) the player gets the
            // register dialog instead of an automatic account
            return autoRegisterUnknown ? Action.FREE_LOGIN : Action.PASSWORD_LOCK;
        }
        if (claimEntryMatches && consent == IdentityClaim.Consent.YES) {
            return Action.CLAIM_AND_LOGIN;
        }
        // No consented claim: consent alone never converts a password account,
        // and missing consent must not prevent its holder from logging in.
        return Action.PASSWORD_LOCK;
    }

    /**
     * Decides for an unverified (offline or same-name cracked) connection.
     * Converted accounts refuse such connections (rule 8); everything else
     * stays behind the password.
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
