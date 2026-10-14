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
     * entry whose pinned UUID matches) is the only path that converts a password
     * account; without one, a consented name falls back to its password (the
     * upgrade can be retried), and an unconsented one is an ownership conflict
     * to refuse (rule 7). A name without an account has no ownership question
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
        if (state.state() == AccountState.BOUND) {
            return verifiedUuid != null && verifiedUuid.equals(state.uuid())
                    ? Action.FREE_LOGIN : Action.REJECT;
        }
        if (!autoLogin) {
            // policy keeps every password account behind its password (D-06 #1)
            return Action.PASSWORD_LOCK;
        }
        if (state.state() == AccountState.OPEN) {
            // decision table row 1: no account, no ownership question. On
            // (default) the unclaimed entry is converted and the player logged
            // in; with auto-register-unknown off (F-15 A) the player gets the
            // register dialog instead of an automatic account
            return autoRegisterUnknown ? Action.FREE_LOGIN : Action.PASSWORD_LOCK;
        }
        if (claimEntryMatches) {
            return Action.CLAIM_AND_LOGIN;
        }
        // no claim to make: the queued entry expired, was lost with a restart,
        // or the upgrade never finished. A consented name keeps its password
        // flow (retriable), an unconsented one is a refused ownership conflict.
        return consent == IdentityClaim.Consent.YES ? Action.PASSWORD_LOCK : Action.REJECT;
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
