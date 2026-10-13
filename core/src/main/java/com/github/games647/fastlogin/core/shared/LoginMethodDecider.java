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

import com.github.games647.fastlogin.protocol.IdentityClaim.Consent;

/**
 * The backend's login-method decision: identity facts from the proxy, account
 * state from the local auth plugin, and the recorded upgrade consent.
 *
 * <p>Decision table (D-03): a verified premium identity without an account is
 * auto-registered and logged in; with an account it needs the upgrade consent
 * (password = ownership, an account never follows a name alone); without the
 * consent the connection is rejected instead of password-locked, because a
 * password lock is a dead end for the verified owner. An unverified connection
 * keeps the password lock; with a consent it means the account switched
 * ownership behind the consent and is rejected symmetrically. Unknown consent
 * (facts that never arrived) never unlocks anything.
 *
 * <p>The account state {@link AccountState#READ_FAILURE} must fail closed to the
 * password lock; callers log it (a database that cannot be read is never a
 * reason to hand out passwordless login).
 */
public final class LoginMethodDecider {

    /** What the local auth plugin reports about the account behind the name. */
    public enum AccountState {
        /** No account: an unclaimed name. */
        NONE,
        /** A password account its owner has not upgraded. */
        PASSWORD_ACCOUNT,
        /**
         * An account the auth plugin itself established as premium (R-7): it is
         * treated like an upgrade consent.
         */
        PREMIUM_OWNED,
        /** The account state could not be read. */
        READ_FAILURE
    }

    /** The login method the backend applies. */
    public enum Outcome {
        /** Auto-register a fresh account and log the player in. */
        AUTO_REGISTER_LOGIN,
        /** Log the player into the existing account without a password. */
        AUTO_LOGIN,
        /** Refuse the connection (account ownership conflict). */
        REJECT,
        /** Keep the auth plugin's password lock. */
        PASSWORD,
        /** Let the auth plugin's registration flow handle the player. */
        GUIDE_REGISTER
    }

    private LoginMethodDecider() {
    }

    /**
     * Applies the decision table to one login with premium handling enabled.
     *
     * @param verifiedPremium whether this connection passed Mojang verification
     * @param account state of the local auth plugin account, including read failure
     * @param consent the name's upgrade consent as the proxy recorded it
     * @return the login method to apply
     */
    public static Outcome decide(boolean verifiedPremium, AccountState account, Consent consent) {
        return decide(verifiedPremium, account, consent, true);
    }

    /**
     * Applies the decision table to one login. The policy keys live on the proxy
     * (R-10) and arrive with the identity facts; this backend only enforces them.
     * With auto-login off no premium handling happens at all (D-06 #1), so the
     * rows collapse to the password lock or the registration flow. Admission
     * (offline-whitelist) and the auto-register-unknown repair are the caller's
     * checks: the first needs the proxy's known list, the second the proxy's
     * registration expectation.
     *
     * @param verifiedPremium whether this connection passed Mojang verification
     * @param account state of the local auth plugin account, including read failure
     * @param consent the name's upgrade consent as the proxy recorded it
     * @param autoLogin the proxy's autoLogin policy value
     * @return the login method to apply
     */
    public static Outcome decide(boolean verifiedPremium, AccountState account, Consent consent,
                                 boolean autoLogin) {
        if (account == AccountState.READ_FAILURE) {
            return Outcome.PASSWORD;
        }

        boolean registered = account != AccountState.NONE;
        // an auth plugin's own premium account is an upgrade consent (R-7)
        Consent effective = account == AccountState.PREMIUM_OWNED ? Consent.YES : consent;

        if (!autoLogin) {
            return registered ? Outcome.PASSWORD : Outcome.GUIDE_REGISTER;
        }

        if (verifiedPremium) {
            if (!registered) {
                return Outcome.AUTO_REGISTER_LOGIN;
            }
            if (effective == Consent.YES) {
                return Outcome.AUTO_LOGIN;
            }
            // Q-B three-state: unknown consent never unlocks anything, but an
            // explicit "no consent" is an ownership conflict to reject (rule 7)
            return effective == Consent.UNKNOWN ? Outcome.PASSWORD : Outcome.REJECT;
        }

        if (!registered) {
            return Outcome.GUIDE_REGISTER;
        }
        // rule 8, the symmetric row: a cracked connection onto a consented
        // premium name means the account moved behind the consent
        return effective == Consent.YES ? Outcome.REJECT : Outcome.PASSWORD;
    }
}
