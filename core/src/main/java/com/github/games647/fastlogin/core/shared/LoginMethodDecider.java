/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
 * consent a password account keeps its password flow. The auth plugin's own
 * premium account counts as consent (R-7), allowing verified owners to recover
 * when the proxy record disagrees. An unverified connection
 * keeps the password lock; with a consent it means the account switched
 * ownership behind the consent and is rejected symmetrically. Unknown consent
 * (facts that never arrived) never unlocks a password account.
 *
 * <p>The account state {@link AccountState#READ_FAILURE} must fail closed to the
 * password lock; callers log it (a database that cannot be read is never a
 * reason to hand out passwordless login).
 *
 * <p>Disabling auto-login prevents all passwordless login, claiming and registration,
 * including R-7 recovery of an already premium-owned account. Only the rejection
 * of an unverified connection onto such an account is policy-independent: that
 * ownership guardrail grants nothing. Verified owners retain the auth plugin's
 * own flow and need an explanation that their converted account has no usable
 * password. Read failure still takes precedence over the guardrail.
 *
 * <p>{@code auto-register-unknown} (the repair for a verified premium player
 * whose local account went missing — deleted, or the databases drifted apart)
 * gates the "verified premium identity without a local account" cell (F-15 A).
 * Its scope: only a connection Mojang has verified whose name has no local
 * account — never an offline/cracked connection, which carries no identity
 * proof and must set its own password in the register dialog. Off = such a
 * verified player gets the register dialog, not an automatic account.
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
     * Resolves a policy value behind a proxy (R-10): the proxy's declared value
     * is the authority and this server's local copy is never even read for it
     * (its config.yml copy is "ignored here"). The local value only serves facts
     * that never arrived — an older proxy during a rolling upgrade — and
     * standalone deployments.
     *
     * @param policyDeclared whether a proxy declared the policy block
     * @param declaredValue the proxy's declared value
     * @param localValue this server's own configured value; only read when nothing was declared
     * @return the value to enforce
     */
    public static boolean resolvePolicyValue(boolean policyDeclared, boolean declaredValue,
                                             java.util.function.Supplier<Boolean> localValue) {
        return policyDeclared ? declaredValue : localValue.get();
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
     * Applies the decision table to one login with the auto-register repair on
     * (the default; see {@link #decide(boolean, AccountState, Consent, boolean,
     * boolean)}).
     *
     * @param verifiedPremium whether this connection passed Mojang verification
     * @param account state of the local auth plugin account, including read failure
     * @param consent the name's upgrade consent as the proxy recorded it
     * @param autoLogin the proxy's autoLogin policy value
     * @return the login method to apply
     */
    public static Outcome decide(boolean verifiedPremium, AccountState account, Consent consent,
                                 boolean autoLogin) {
        return decide(verifiedPremium, account, consent, autoLogin, true);
    }

    /**
     * Applies the decision table to one login. The policy keys live on the proxy
     * (R-10) and arrive with the identity facts; this backend only enforces them.
     * With auto-login off other rows collapse to the password lock or registration
     * flow; only the unverified premium-owned rejection precedes policy. R-7
     * recovery requires auto-login to be enabled.
     * Admission
     * (offline-whitelist) is the caller's check: it needs the proxy's known list.
     *
     * <p>{@code autoRegisterUnknown} gates the "verified premium identity without
     * a local account" cell (F-15 A — the key name misleads: "unknown" is the
     * missing local account of a proven identity, not strangers/offline names):
     * on (default) the account is auto-created for the proven identity, off the
     * player gets the register dialog and sets their own password. Offline
     * connections never reach this cell with {@code verifiedPremium} set.</p>
     *
     * @param verifiedPremium whether this connection passed Mojang verification
     * @param account state of the local auth plugin account, including read failure
     * @param consent the name's upgrade consent as the proxy recorded it
     * @param autoLogin the proxy's autoLogin policy value
     * @param autoRegisterUnknown the proxy's auto-register-unknown policy value
     * @return the login method to apply
     */
    public static Outcome decide(boolean verifiedPremium, AccountState account, Consent consent,
                                 boolean autoLogin, boolean autoRegisterUnknown) {
        if (account == AccountState.READ_FAILURE) {
            return Outcome.PASSWORD;
        }
        if (!verifiedPremium && account == AccountState.PREMIUM_OWNED) {
            // This guardrail grants nothing and must survive policy changes.
            return Outcome.REJECT;
        }

        boolean registered = account != AccountState.NONE;

        if (!autoLogin) {
            return registered ? Outcome.PASSWORD : Outcome.GUIDE_REGISTER;
        }
        if (account == AccountState.PREMIUM_OWNED) {
            return Outcome.AUTO_LOGIN;
        }

        if (verifiedPremium) {
            if (!registered) {
                return autoRegisterUnknown ? Outcome.AUTO_REGISTER_LOGIN : Outcome.GUIDE_REGISTER;
            }
            if (consent == Consent.YES) {
                return Outcome.AUTO_LOGIN;
            }
            return account == AccountState.PASSWORD_ACCOUNT ? Outcome.PASSWORD : Outcome.REJECT;
        }

        if (!registered) {
            return Outcome.GUIDE_REGISTER;
        }
        // rule 8, the symmetric row: a cracked connection onto a consented
        // premium name means the account moved behind the consent
        return consent == Consent.YES ? Outcome.REJECT : Outcome.PASSWORD;
    }
}
