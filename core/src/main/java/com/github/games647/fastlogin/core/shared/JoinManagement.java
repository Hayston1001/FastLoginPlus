/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.config.Configuration;

import java.io.IOException;
import java.util.Optional;

public abstract class JoinManagement<P extends C, C, S extends LoginSource> {

    protected final FastLoginCore<P, C, ?> core;
    protected final AuthPlugin<P> authHook;
    private final BedrockService<?> bedrockService;

    public JoinManagement(FastLoginCore<P, C, ?> core, AuthPlugin<P> authHook, BedrockService<?> bedrockService) {
        this.core = core;
        this.authHook = authHook;
        this.bedrockService = bedrockService;
    }

    public void onLogin(String username, S source) {
        core.settlePendingVerification(username);
        //check if the player is connecting through Bedrock Edition
        if (bedrockService != null && bedrockService.isBedrockConnection(username)) {
            //perform Bedrock specific checks and skip Java checks if no longer needed
            if (bedrockService.performChecks(username, source)) {
                return;
            }
        }

        String ip = source.getAddress().getAddress().getHostAddress();

        // load and mutate an existing profile under the name-level
        // striped lock so a concurrent admin toggle or plugin-message save for the
        // same player cannot interleave with this window.  The new-player branch
        // performs Mojang API lookups (network I/O) and must not hold the lock —
        // it runs for profiles without an existing row, so there is nothing to
        // lose for it; its later persistence goes through wrapped save windows.
        StoredProfile newPlayerProfile = core.getStorage().withNameLock(username, () -> {
            StoredProfile profile = core.getStorage().loadProfile(username);
            //can't be a premium Java player, if it's not saved in the database
            if (profile == null) {
                return null;
            }
            if (profile.isUuidRepairRequired()) {
                source.kick("FastLoginPlus: invalid premium UUID; contact the server administrator");
                return null;
            }

            if (profile.isFloodgateMigrated()) {
                if (profile.getFloodgate() == FloodgateState.TRUE) {
                    // migrated and enabled floodgate player, however the above
                    // bedrocks fails, so the current connection isn't premium
                    return null;
                }
            } else {
                profile.setFloodgate(FloodgateState.FALSE);
                if (core.isDebug()) {
                    core.getPlugin().getLog().info(
                            "Player {} will be migrated to the v2 database schema as a JAVA user", username
                    );
                }
            }

            callFastLoginPreLoginEvent(username, source, profile);

            profile.setLastIp(ip);
            // reading the observation follows the configured fallback semantics:
            // with secondAttemptCracked off a marked name is treated as if
            // it had never been recorded, so it verifies again — and a passing
            // verification then clears the stale mark. The read side is the
            // authority; the startup reconciliation only keeps the database tidy.
            boolean observedCracked = profile.isSameNameCracked() && isFallbackEnabled();
            boolean hasRealRecord = profile.isOnlinemodePreferred() || profile.getId() != null;
            // a row that holds nothing but a dormant observation — the mark is
            // stored, but the fallback is off and there is neither consent nor a
            // premium UUID — is treated as if it had never been recorded
            boolean dormantObservationOnly = profile.isSameNameCracked() && !observedCracked && !hasRealRecord;
            if (profile.isExistingPlayer() && !dormantObservationOnly) {
                // routing follows the record's facts: a consented name or a
                // name whose premium UUID observation is still intact is verified
                // again; everything else keeps the password flow
                boolean premiumName = profile.isOnlinemodePreferred()
                        || (profile.getId() != null && !observedCracked);
                if (premiumName) {
                    // the cracked fallback only applies to names without consent: a
                    // consented name is verified on every connection (an impostor can
                    // never drag it into the password flow), while a name whose premium
                    // identity is not consented can log in with its password and upgrade
                    // afterwards
                    if (!profile.isOnlinemodePreferred() && shouldFallBackToCracked(username)) {
                        if (core.isDebug()) {
                            core.getPlugin().getLog()
                                    .info("Unverified premium attempt -> cracked {}", username);
                        }
                        confirmSameNameCracked(username, profile);
                        if (isValidUsername(source, profile)) {
                            startCrackedSession(source, profile, username);
                        }
                        return null;
                    }

                    if (core.isDebug()) {
                        core.getPlugin().getLog()
                            .info("Requesting premium login for registered player: {}", username);
                    }
                    if (canUsePremiumAccount(username, profile.getId())) {
                        requestPremiumLogin(source, profile, username, true);
                    } else {
                        source.kick("FastLoginPlus: account ownership conflict");
                    }
                } else {
                    if (isValidUsername(source, profile)) {
                        startCrackedSession(source, profile, username);
                    }
                }
                return null;
            }

            // brand-new player, or a name whose only record is an ignored
            // observation: both get the same decision as an unknown name
            // (including the premium check), run outside the lock
            return profile;
        });

        if (newPlayerProfile != null) {
            performNewPlayerLogin(username, source, newPlayerProfile);
        }
    }

    private void performNewPlayerLogin(String username, S source, StoredProfile profile) {
        try {
            if (shouldFallBackToCracked(username)) {
                if (core.isDebug()) {
                    core.getPlugin().getLog().info("Unverified premium attempt -> cracked {}", username);
                }

                // the previous attempt never verified: record the persistent
                // same-name cracked observation and open the password flow directly
                confirmSameNameCracked(username, profile);
                startCrackedSession(source, profile, username);
                return;
            }

            Configuration config = core.getConfig();
            Optional<Profile> premiumUUID = Optional.empty();
            if ((boolean) config.get("nameChangeCheck") || (boolean) config.get("autoRegister")
                    || (boolean) config.get("offline-whitelist")) {
                int retryCount = Math.max(1,
                        ((Number) config.get("mojang-retry-count")).intValue());
                long retryDelay =
                        ((Number) config.get("mojang-retry-delay")).longValue();

                boolean checkedPremium = false;
                for (int attempt = 1; attempt <= retryCount; attempt++) {
                    try {
                        premiumUUID = core.getResolver().findProfile(username);
                        checkedPremium = true;
                        break;
                    } catch (RateLimitException rateLimitEx) {
                        core.getPlugin().getLog().error(
                                "Mojang's rate limit reached for {}."
                                        + " The public IPv4 address of this server issued"
                                        + " more than 600 Name -> UUID requests within 10 minutes."
                                        + " After those 10 minutes we can make requests again.",
                                username);
                        break;
                    } catch (IOException ioEx) {
                        if (attempt < retryCount) {
                            core.getPlugin().getLog().warn(
                                    "Mojang profile lookup failed for {} (attempt {}/{}): {}",
                                    username, attempt, retryCount, ioEx.getMessage());
                            try {
                                long backoff = Math.min(
                                        retryDelay * (1L << (attempt - 1)), 10_000L);
                                Thread.sleep(backoff);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                core.getPlugin().getLog().warn(
                                        "Interrupted during Mojang profile lookup retry for {}",
                                        username);
                                break;
                            }
                        } else {
                            core.getPlugin().getLog().error(
                                    "Mojang profile lookup exhausted all {} retries for {}",
                                    retryCount, username, ioEx);
                        }
                    } catch (Exception ex) {
                        core.getPlugin().getLog().error(
                                "Unexpected error during Mojang profile lookup for {}",
                                username, ex);
                        break;
                    }
                }

                if (!checkedPremium) {
                    // "could not tell": a lookup that never completed —
                    // rate limit, network trouble or an interruption — says
                    // nothing about the name. It must not degrade the player
                    // into the password flow: a new premium player would then
                    // register a password account without the premium UUID
                    // observation, and per the routing would never verify again
                    // (the passwordless login would be lost for good).
                    core.getPlugin().getLog().warn(
                            "Could not determine whether {} owns a premium account: the Mojang"
                                    + " lookup did not complete. Refusing to guess.", username);
                    source.kick(core.getMessage("auth-server-unreachable"));
                    return;
                }
            }

            if (!premiumUUID.isPresent()
                    || (!isNameChanged(source, username, premiumUUID.get())
                    && !isUsernameAvailable(source, username, profile, premiumUUID.get()))) {
                //nothing detected the player as premium -> start a cracked session
                if ((boolean) core.getConfig().get("offline-whitelist")) {
                    source.kick(core.getMessage("offline-whitelist-kick-message"));
                    return;
                }

                startCrackedSession(source, profile, username);
            }
        } catch (Exception ex) {
            core.getPlugin().getLog().error("Failed to check premium state of {}", username, ex);
            // Fail closed: an error in the decision tree must not
            // open a registration window — kick like the whitelist miss above
            if ((boolean) core.getConfig().get("offline-whitelist")) {
                try {
                    source.kick(core.getMessage("offline-whitelist-kick-message"));
                } catch (Exception kickEx) {
                    core.getPlugin().getLog().error(
                            "Failed to kick {} while failing closed on the offline-whitelist",
                            username, kickEx);
                }
            }
        }
    }

    protected boolean isValidUsername(LoginSource source, StoredProfile profile) {
        if (bedrockService != null && bedrockService.isUsernameForbidden(profile)) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Floodgate Prefix detected on cracked player");
            }
            source.kick("Your username contains illegal characters");
            return false;
        }

        return true;
    }

    private boolean isUsernameAvailable(S source, String username, StoredProfile profile, Profile mojang)
            throws Exception {
        if (core.isDebug()) {
            core.getPlugin().getLog().info("GameProfile {} uses a premium username", username);
        }
        // Fabric can use this path only after its backend has confirmed account ownership.
        if (!canUsePremiumAccount(username, mojang.getId())) {
            return false;
        }
        if (isPreRegisteredPremiumAccount(username, mojang.getId())) {
            requestPremiumLogin(source, profile, username, true);
            return true;
        }
        if ((boolean) core.getConfig().get("autoRegister") && (authHook == null || !authHook.isRegistered(username))) {
            requestPremiumLogin(source, profile, username, false);
            return true;
        }

        if ((boolean) core.getConfig().get("offline-whitelist")) {
            // Only gate access — registration is autoRegister's responsibility
            requestPremiumLogin(source, profile, username, true);
            return true;
        }

        return false;
    }

    private boolean isNameChanged(S source, String username, Profile profile) {
        //user not exists in the db
        if ((boolean) core.getConfig().get("nameChangeCheck")) {
            StoredProfile storedProfile = core.getStorage().loadProfile(profile.getId());
            if (storedProfile != null) {
                if (storedProfile.getFloodgate() == FloodgateState.TRUE) {
                    if (core.isDebug()) {
                        core.getPlugin().getLog()
                                .info("Player {} is already stored by FastLogin"
                                        + " as a Bedrock Edition player.", username);
                    }
                    return false;
                }

                //uuid exists in the database
                if (core.isDebug()) {
                    core.getPlugin().getLog().info("GameProfile {} changed it's username", profile);
                }

                if (canUsePremiumAccount(username, profile.getId())) {
                    //update the username to the new one in the database
                    storedProfile.setPlayerName(username);
                    requestPremiumLogin(source, storedProfile, username, false);
                    return true;
                }
            }
        }

        return false;
    }

    public abstract FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, S source, StoredProfile profile);

    public abstract void requestPremiumLogin(S source, StoredProfile profile, String username, boolean registered);

    public abstract void startCrackedSession(S source, StoredProfile profile, String username);

    /**
     * Whether the configured cracked fallback is active ({@code secondAttemptCracked}).
     * The same switch governs reading a stored observation: with it off a recorded
     * name is treated as never marked.
     *
     * @return true when the fallback and the observation semantics are enabled
     */
    protected boolean isFallbackEnabled() {
        return (boolean) core.getConfig().get("secondAttemptCracked");
    }

    /**
     * Whether repeated unverified premium attempts for the name open the cracked
     * fallback. Requires the {@code secondAttemptCracked} fallback to be enabled
     * and one settled, definitive "not premium" verdict — an attempt that
     * merely never verified counts only once the settlement judged it failed.
     *
     * @param username exact login name
     * @return true when the name should skip the premium attempt
     */
    protected boolean shouldFallBackToCracked(String username) {
        return isFallbackEnabled() && core.getDefiniteFailures(username) >= 1;
    }

    /**
     * Records the same-name cracked observation on the name's row: a
     * premium verification for this name was observed to fail and none passed
     * since. The observation is per name and persistent, so later connections go
     * straight to the password flow instead of failing one connection first.
     *
     * <p>Only the confirmation that actually flips the flag queues the one-time
     * chat notice: a record that already exists must not nag on every join.</p>
     *
     * @param username exact login name
     * @param profile the name's record (new or existing)
     */
    protected void confirmSameNameCracked(String username, StoredProfile profile) {
        SQLStorage storage = core.getStorage();
        boolean firstConfirmation = storage.withNameLock(username, () -> {
            boolean first = !profile.isSameNameCracked();
            profile.setSameNameCracked(true);
            storage.save(profile);
            return first;
        });
        if (firstConfirmation) {
            core.queueSameNameCrackedNotice(username);
        }
    }

    /**
     * The Fabric mode keeps undecided names reserved after Mojang lookup failures.
     *
     * <p>Unused: an unfinished Mojang lookup is "could not tell", and the
     * unified handling is a kick that explains why
     * ({@code auth-server-unreachable}) — reserving the name would keep a
     * degraded, half-known state instead. This hook has no implementer in the
     * tree (the Fabric mod runs its own flow) and is kept only so out-of-tree
     * subclasses keep compiling.</p>
     *
     * @return true when this login path enforces the reservation
     */
    @Deprecated
    protected boolean shouldReserveUnknownPremiumNames() {
        return false;
    }

    /**
     * Checks backend ownership before requesting a premium session.
     * @param username exact login name
     * @param mojangUuid known Mojang UUID, or null for a stored profile without one
     * @return true when premium login may be attempted
     */
    protected boolean canUsePremiumAccount(String username, java.util.UUID mojangUuid) {
        return true;
    }

    /**
     * Reports a backend premium account that FLP has not recorded yet.
     * @param username exact login name
     * @param mojangUuid Mojang UUID from name lookup
     * @return true when the backend account is already bound to that UUID
     */
    protected boolean isPreRegisteredPremiumAccount(String username, java.util.UUID mojangUuid) {
        return false;
    }

    /**
     * Fallback for a decision without a completed Mojang lookup.
     *
     * <p>Unused, alongside {@link #shouldReserveUnknownPremiumNames()}:
     * an unfinished lookup now
     * kicks with {@code auth-server-unreachable} instead of starting any
     * session. Kept so out-of-tree subclasses keep compiling.</p>
     *
     * @param source this connection
     * @param profile the unresolved stored profile
     * @param username the requested name
     */
    @Deprecated
    protected void startUndecidedSession(S source, StoredProfile profile, String username) {
        startCrackedSession(source, profile, username);
    }
}
