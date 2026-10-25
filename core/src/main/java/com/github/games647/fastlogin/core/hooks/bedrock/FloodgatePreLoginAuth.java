/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.hooks.bedrock;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin.Registration;
import com.github.games647.fastlogin.core.shared.FastLoginCore;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers and logs in a Floodgate player before AuthMe's login gate starts.
 *
 * <p>Both signed proxy facts and the optional backend Floodgate API supply a
 * trusted Bedrock session to the same decision and cleanup logic.
 *
 * <p>AuthMe 6.0.1 exposes name-based
 * {@code AuthMeApi#registerPlayer(String, String)} and
 * {@code AuthMeApi#forceLoginFromProxy(String)}. The latter queues the login (or
 * approves an open pre-join dialog), so the record exists and the login is
 * already requested before AuthMe's register/login timeout can fire. The
 * generated password stays on this path: it is never sent to the Bedrock player
 * or written to a log.
 */
public class FloodgatePreLoginAuth {

    /** What the backend pre-login path did. */
    public enum Outcome {
        /** The auth plugin's name-based API is not available. */
        UNSUPPORTED,
        /** The account state could not be read, so no login state was changed. */
        ACCOUNT_READ_FAILED,
        /** The Bedrock name conflicts a premium Java account. */
        NAME_CONFLICT,
        /** No account exists and autoRegisterFloodgate does not allow one. */
        NO_AUTO_REGISTER,
        /** Auto-login is disallowed, including registration that would require it. */
        NO_AUTO_LOGIN,
        /** AuthMe did not create the account. */
        REGISTRATION_FAILED,
        /** Login failed, but the captured new account was synchronously removed and read back as absent. */
        REGISTERED_LOGIN_FAILED,
        /** Login failed and safe cleanup is unavailable; the account needs operator repair. */
        REGISTERED_LOGIN_CLEANUP_FAILED,
        /** The account exists but AuthMe did not accept the force login. */
        FORCE_LOGIN_FAILED,
        /** A new account was created and its pre-join login was requested. */
        REGISTERED_AND_LOGIN_REQUESTED,
        /** An existing account's pre-join login was requested. */
        LOGIN_REQUESTED
    }

    private final FastLoginCore<?, ?, ?> core;

    public FloodgatePreLoginAuth(FastLoginCore<?, ?, ?> core) {
        this.core = core;
    }

    /**
     * Applies the configured Floodgate auto-auth policy for one Bedrock
     * connection. Callers must have verified that the connection is a Floodgate
     * player and that {@code auth} is the backend's auth hook.
     *
     * @param username the name the backend will see
     * @param session the trusted Bedrock facts and policy for this connection
     * @param auth the backend auth plugin, behind its Floodgate pre-login bridge
     * @return what happened; never null
     */
    public Outcome handle(String username, BedrockSession session, PreLoginAuthPlugin auth) {
        if (!auth.isPreLoginForceLoginSupported()) {
            // Do not create an account we cannot log in to. Declaration owners must still suppress
            // alternative auto-auth; only no-declaration compatibility callers may use a legacy path.
            return Outcome.UNSUPPORTED;
        }

        String autoLogin = session.getAutoLogin();
        String autoRegister = session.getAutoRegister();
        String allowConflict = session.getAllowConflict();
        boolean linked = session.isLinked();

        if (core.isDebug()) {
            core.getPlugin().getLog().info(
                    "Backend Floodgate pre-login for {} (linked={}, autoLoginFloodgate={}, "
                            + "autoRegisterFloodgate={}, allowFloodgateNameConflict={})",
                    username, linked, autoLogin, autoRegister, allowConflict);
        }

        boolean registered;
        try {
            registered = auth.isRegistered(username);
        } catch (Exception readError) {
            // Exceptions from auth plugins may contain database details or
            // credentials. Keep the type only.
            core.getPlugin().getLog().warn(
                    "Could not read the auth account state of {} during backend Floodgate "
                            + "pre-login (errorType={}); keeping the existing login flow",
                    username, readError.getClass().getSimpleName());
            return Outcome.ACCOUNT_READ_FAILED;
        }

        if (needsNameConflictCheck(linked, allowConflict, autoLogin, autoRegister, registered)
                && hasNameConflict(username)) {
            return Outcome.NAME_CONFLICT;
        }

        if (registered) {
            if (!BedrockSession.isAutoAuthAllowed(autoLogin, linked)) {
                if (core.isDebug()) {
                    core.getPlugin().getLog().info(
                            "Skipping backend Floodgate auto-login for {}: "
                                    + "autoLoginFloodgate={} (linked={})",
                            username, autoLogin, linked);
                }
                return Outcome.NO_AUTO_LOGIN;
            }

            if (!auth.forceLoginFromProxy(username)) {
                core.getPlugin().getLog().warn(
                        "AuthMe did not accept the backend pre-join force login for existing "
                                + "Bedrock player {}", username);
                return Outcome.FORCE_LOGIN_FAILED;
            }

            if (core.isDebug()) {
                core.getPlugin().getLog().info(
                        "Queued AuthMe's pre-join force login for existing Bedrock player {}",
                        username);
            }
            return Outcome.LOGIN_REQUESTED;
        }

        if (!BedrockSession.isAutoAuthAllowed(autoRegister, linked)) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info(
                        "Skipping backend Floodgate auto-register for {}: "
                                + "autoRegisterFloodgate={} (linked={})",
                        username, autoRegister, linked);
            }
            return Outcome.NO_AUTO_REGISTER;
        }

        if (!BedrockSession.isAutoAuthAllowed(autoLogin, linked)) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info(
                        "Skipping backend Floodgate registration for {}: auto-login is disallowed", username);
            }
            return Outcome.NO_AUTO_LOGIN;
        }

        String generatedPassword = generatePassword();
        boolean created;
        try {
            created = auth.registerPlayer(username, generatedPassword);
        } catch (RuntimeException registrationError) {
            core.getPlugin().getLog().warn(
                    "Could not create the AuthMe account for {} during backend Floodgate "
                            + "pre-login (errorType={})",
                    username, registrationError.getClass().getSimpleName());
            return Outcome.REGISTRATION_FAILED;
        }

        Registration registration = created ? captureRegistration(username, auth) : null;
        if (!created) {
            // registerPlayer also reports false in a race where the row appeared
            // between our check and the insert. Only a still-absent row is a
            // failure; never force a login against a missing record.
            if (!isRegisteredNow(username, auth) || !BedrockSession.isAutoAuthAllowed(autoLogin, linked)) {
                core.getPlugin().getLog().warn(
                        "AuthMe did not create an account for Bedrock player {}; "
                                + "keeping the existing login flow", username);
                return Outcome.REGISTRATION_FAILED;
            }
        }

        if (!auth.forceLoginFromProxy(username)) {
            if (!created) {
                // A false insert never establishes ownership of the record for cleanup.
                return Outcome.FORCE_LOGIN_FAILED;
            }
            return cleanupFailedRegistration(username, registration, auth);
        }

        if (core.isDebug()) {
            core.getPlugin().getLog().info(
                    "Queued backend Floodgate pre-join login for {} (created={})", username, created);
        }
        return created ? Outcome.REGISTERED_AND_LOGIN_REQUESTED : Outcome.LOGIN_REQUESTED;
    }

    private Registration captureRegistration(String username, PreLoginAuthPlugin auth) {
        try {
            Registration registration = auth.captureRegistration(username);
            if (registration != null
                    && username.toLowerCase(Locale.ROOT).equals(registration.getPlayerName())) {
                return registration;
            }
        } catch (RuntimeException captureError) {
            core.getPlugin().getLog().error(
                    "Backend Floodgate registration snapshot failed for {} (errorType={})",
                    username, captureError.getClass().getSimpleName());
        }
        return null;
    }

    private Outcome cleanupFailedRegistration(String username, Registration registration, PreLoginAuthPlugin auth) {
        boolean deleted = false;
        try {
            deleted = registration != null && auth.deleteRegistration(registration);
        } catch (RuntimeException cleanupError) {
            core.getPlugin().getLog().error(
                    "Backend Floodgate account cleanup failed for {} (errorType={})",
                    username, cleanupError.getClass().getSimpleName());
        }
        if (!deleted) {
            core.getPlugin().getLog().error(
                    "Backend Floodgate pre-join login failed for {}; snapshot-checked AuthMe cleanup "
                            + "was unavailable or could not be verified. Operator repair is required", username);
            return Outcome.REGISTERED_LOGIN_CLEANUP_FAILED;
        }
        core.getPlugin().getLog().warn(
                "Backend Floodgate pre-join login failed for {}; the captured AuthMe registration "
                        + "was synchronously deleted and read back as absent", username);
        return Outcome.REGISTERED_LOGIN_FAILED;
    }

    private boolean isRegisteredNow(String username, PreLoginAuthPlugin auth) {
        try {
            return auth.isRegistered(username);
        } catch (Exception ignored) {
            return false;
        }
    }

    /*
     * Mirrors FloodgateManagement's name-conflict decision without
     * needing a LoginSource. When the proxy already enforces the
     * conflict policy, a premium name must still not gain a new backend account.
     */
    private boolean needsNameConflictCheck(boolean linked, String allowConflict, String autoLogin,
                                           String autoRegister, boolean registered) {
        if (linked) {
            return false;
        }
        if ("false".equals(allowConflict) || "linked".equals(allowConflict)) {
            return true;
        }
        return "no-conflict".equals(autoLogin) || !registered && "no-conflict".equals(autoRegister);
    }

    private boolean hasNameConflict(String username) {
        Optional<Profile> premiumProfile;
        try {
            premiumProfile = core.getResolver().findProfile(username);
        } catch (IOException | RateLimitException lookupError) {
            // Fail closed for this new path: no auto-register or auto-login on a
            // name we could not check. The existing proxy flow still applies.
            core.getPlugin().getLog().warn(
                    "Could not check whether Bedrock player {}'s name conflicts a premium "
                            + "Java account (errorType={}); keeping the existing login flow",
                    username, lookupError.getClass().getSimpleName());
            return true;
        }

        if (premiumProfile.isPresent()) {
            core.getPlugin().getLog().warn(
                    "Bedrock player {}'s name conflicts an existing premium Java account; "
                            + "keeping the existing login flow", username);
            return true;
        }
        return false;
    }

    private String generatePassword() {
        // No Player exists yet, so a platform password generator cannot safely be called.
        return UUID.randomUUID().toString().replace("-", "");
    }

}
