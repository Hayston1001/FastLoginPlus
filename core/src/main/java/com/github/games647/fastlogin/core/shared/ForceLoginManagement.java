/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;

public abstract class ForceLoginManagement<P extends C, C, L extends LoginSession, T extends PlatformPlugin<C>>
        implements Runnable {

    protected final FastLoginCore<P, C, T> core;
    protected final P player;
    protected final L session;

    public ForceLoginManagement(FastLoginCore<P, C, T> core, P player, L session) {
        this.core = core;
        this.player = player;
        this.session = session;
    }

    @Override
    public void run() {
        premiumHandlingAllowed = false;
        if (!isOnline(player)) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Player {} disconnected", player);
            }
            return;
        }

        if (session == null) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("No valid session found for {}", player);
            }
            return;
        }

        SQLStorage storage = core.getStorage();
        StoredProfile playerProfile = session.getProfile();
        try {
            if (!isOnlineMode() && session.isFactsPresent() && core.getAuthPluginHook() != null) {
                // Only a proxy-facts session takes this new path. Standalone
                // offline bookkeeping and premium verification remain unchanged.
                runAuthPluginFlow(storage, playerProfile, core.getAuthPluginHook());
                return;
            }
            if (isOnlineMode()) {
                //premium player
                // a passing verification refutes the same-name cracked observation
                core.resetVerificationState(getName(player));
                AuthPlugin<P> authPlugin = core.getAuthPluginHook();
                if (authPlugin == null) {
                    // maybe only bungeecord plugin
                    // The proxy has already verified this session (online mode), so persist
                    // the premium observation locally instead of relying on the backend's
                    // SuccessMessage ack. With AuthMe 6.0 the ack is never sent: on a
                    // REGISTER action the backend skips ForceLoginTask when the AuthMe record
                    // already exists (pre-created), and on a LOGIN action forceLogin() returns
                    // false because AsynchronousJoin already authenticated the player. Without
                    // a row every reconnect is treated as a new player (performNewPlayerLogin)
                    // and the cracked fallback can let a verified premium player join offline
                    // after a session expiry.
                    // Only the observation is written here: the upgrade consent is the
                    // backend's to confirm (SuccessMessage) or the player's to give (the
                    // premium toggle). Writing it before the backend decided is exactly the
                    // rejected-takeover leak this record model closes.
                    // Note: Floodgate forced-online sessions reach this branch too; their
                    // session UUID is never set (onGameProfileRequest only handles online-mode
                    // connections), so the row is written without a UUID — matching the
                    // existing Floodgate row convention.
                    onForceActionSuccess(session);

                    if (playerProfile != null) {
                        // persist under the name-level striped lock
                        storage.withNameLock(getName(player), () -> {
                            playerProfile.setId(session.getUuid());
                            playerProfile.setSameNameCracked(false);
                            storage.save(playerProfile);
                        });
                    }
                } else {
                    runAuthPluginFlow(storage, playerProfile, authPlugin);
                }
            } else if (playerProfile != null) {
                //cracked player: only the bookkeeping fields are saved. The record's
                // observations (premium UUID, same-name cracked) and the consent survive
                // an offline join — a cracked connection under a premium name is exactly
                // the same-name cracked case, and only an explicit toggle revokes consent.
                storage.withNameLock(getName(player), () -> {
                    storage.save(playerProfile);
                });

                // the one-time notice for a freshly confirmed same-name cracked
                // observation. Delivered here — when the player has
                // actually reached the game — because the login phase has no chat
                // yet; it goes only to the password flow the observation caused,
                // and only while the observation is still recorded.
                notifySameNameCracked(playerProfile);
            }
        } catch (Exception ex) {
            core.getPlugin().getLog().warn("ERROR ON FORCE LOGIN of {}", getName(player), ex);
        }
    }

    /**
     * Delivers a first-confirmation notice; proxies may negotiate backend-local rendering.
     *
     * @param profile the offline session's current stored observation
     */
    protected void notifySameNameCracked(StoredProfile profile) {
        if (core.consumeSameNameCrackedNotice(getName(player)) && profile.isSameNameCracked()) {
            core.sendLocaleMessage("same-name-cracked-recorded", player);
        }
    }

    /**
     * The login flow for a verified premium session with an auth plugin: the
     * message path applies the decision table, the standalone path keeps
     * its local keys, and only confirmed outcomes touch the record.
     *
     * @param storage the storage to persist the record with
     * @param playerProfile the name's record, or null on proxy-mode backends
     * @param authPlugin the auth plugin hook
     * @throws Exception when the auth plugin query fails (handled by the caller)
     */
    private void runAuthPluginFlow(SQLStorage storage, StoredProfile playerProfile, AuthPlugin<P> authPlugin)
            throws Exception {
        String playerName = getName(player);
        boolean success = true;
        boolean recoveringPremiumAccount = false;
        if (session.isFactsPresent()) {
            // The message path: a proxy exists, so the decision table
            // decides from the account state plus its facts.
            LoginMethodDecider.AccountState account = readAccountState(authPlugin, playerName);
            if (!session.getFacts().policyDeclared()) {
                core.getPlugin().getLog().warn(
                        "Proxy did not declare identity facts for {}; falling back to "
                                + "conservative behaviour", playerName);
            }
            boolean autoLogin = resolveAutoLogin(session);
            LoginMethodDecider.Outcome outcome = LoginMethodDecider.decide(isOnlineMode(), account,
                    session.getFacts().consent(), autoLogin,
                    resolveAutoRegisterUnknown(session));
            switch (outcome) {
                case AUTO_REGISTER_LOGIN:
                    premiumHandlingAllowed = true;
                    success = forceRegister(player);
                    break;
                case AUTO_LOGIN:
                    premiumHandlingAllowed = true;
                    boolean cancelled = callFastLoginAutoLoginEvent(session, playerProfile).isCancelled();
                    success = !cancelled && forceLogin(player);
                    recoveringPremiumAccount = !cancelled
                            && account == LoginMethodDecider.AccountState.PREMIUM_OWNED
                            && session.getConsent()
                            != com.github.games647.fastlogin.protocol.IdentityClaim.Consent.YES;
                    break;
                case REJECT:
                    denyLogin(player, core.getMessage("ownership-conflict"));
                    return;
                case PASSWORD:
                case GUIDE_REGISTER:
                default:
                    // nothing to do: the auth plugin keeps its password
                    // or registration flow, and no premium row is
                    // written, so an undeclared consent can never
                    // turn into one
                    if (core.isDebug()) {
                        core.getPlugin().getLog().info(
                                "Keeping the auth plugin's own login flow for {}", playerName);
                    }
                    if (account == LoginMethodDecider.AccountState.READ_FAILURE) {
                        core.getPlugin().sendMessage(player,
                                core.getMessage("account-state-unavailable"));
                    } else if (isOnlineMode() && !autoLogin
                            && account == LoginMethodDecider.AccountState.PREMIUM_OWNED) {
                        notifyPremiumPolicyDisabled();
                    } else if (isOnlineMode()
                            && account == LoginMethodDecider.AccountState.PASSWORD_ACCOUNT) {
                        // Only a connection that actually passed verification may be told
                        // that its premium identity was verified. An unverified password
                        // account keeps the auth plugin's own login/registration flow
                        // without the premium notice.
                        notifyPasswordLogin();
                    }
                    return;
            }
        } else if ((boolean) core.getConfig().get("autoLogin")) {
            premiumHandlingAllowed = true;
            // Standalone deployment: no proxy declared anything, so the
            // local keys and the session's registration expectation
            // decide, exactly as before the decision table landed.
            // The "verified premium + no local account" cell is gated by
            // auto-register-unknown: on = auto-create for the proven
            // identity, off = the auth plugin's own register dialog, never an
            // automatic account. This whole branch is inside isOnlineMode(), so
            // offline/cracked connections never reach it. The value follows
            // the declared policy first, the local key only when nothing
            // was declared (standalone).
            boolean autoRegisterUnknown = resolveAutoRegisterUnknown(session);
            boolean needsAccount = session.needsRegistration()
                    || !authPlugin.isRegistered(playerName);
            if (needsAccount && autoRegisterUnknown) {
                success = forceRegister(player);
            } else if (needsAccount) {
                // guide register: the auth plugin's own dialog, the player sets
                // the password (the decision table's GUIDE_REGISTER outcome)
                if (core.isDebug()) {
                    core.getPlugin().getLog().info(
                            "Not auto-registering {} (auto-register-unknown is off): "
                                    + "the auth plugin's own register flow applies", playerName);
                }
                return;
            } else if (!callFastLoginAutoLoginEvent(session, playerProfile).isCancelled()) {
                success = forceLogin(player);
            }
        }

        if (success) {
            //update only on success to prevent corrupt data
            if (playerProfile != null) {
                // persist under the name-level striped lock. This write is the
                // confirmed outcome: the account now belongs to the verified
                // premium identity, which is what the consent records.
                storage.withNameLock(getName(player), () -> {
                    playerProfile.setId(session.getUuid());
                    playerProfile.setSameNameCracked(false);
                    playerProfile.setOnlinemodePreferred(true);
                    storage.save(playerProfile);
                });
            }

            onForceActionSuccess(session);
        } else {
            // forceLogin/forceRegister returned false but this is a verified
            // premium session. This happens with AuthMe 6.0: AuthMe's
            // AsynchronousJoin runs canBypassWithPremium() before FLP's
            // ForceLoginTask, so the player is already authenticated when
            // forceLogin() is called (returns false).
            // We still need to persist onlinemodePreferred=true so that future
            // reconnects route through requestPremiumLogin instead of
            // startCrackedSession (direct mode; proxy-mode backends keep
            // profile=null, so nothing is saved there).
            if (playerProfile != null) {
                // persist under the name-level striped lock. The player is
                // authenticated as this verified identity (AuthMe 6.0's
                // bypass), so this is again a confirmed outcome.
                storage.withNameLock(getName(player), () -> {
                    playerProfile.setId(session.getUuid());
                    playerProfile.setSameNameCracked(false);
                    playerProfile.setOnlinemodePreferred(true);
                    storage.save(playerProfile);
                });

            }
            // Ack the proxy even when the auth plugin reported failure: the
            // session is already verified and the player is in game. Without
            // this ack the proxy never persists the premium row and
            // secondAttemptCracked can let the player join offline after a
            // session expiry.
            onForceActionSuccess(session);
        }
        if (recoveringPremiumAccount) {
            core.getPlugin().getLog().warn(
                    "Recovered verified premium login for {} from the auth plugin's premium account "
                            + "despite proxy consent {}; requested proxy resynchronization",
                    playerName, session.getConsent());
        }
    }

    public boolean forceRegister(P player) {
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Register player {}", getName(player));
        }

        String generatedPassword = core.getPasswordGenerator().getRandomPassword(player);
        AuthPlugin<P> authPlugin = core.getAuthPluginHook();
        boolean success = authPlugin.forceRegister(player, generatedPassword);

        // only announce the generated password when the auth plugin really stored it.
        // AuthMe 6.0's takeover pre-creates the record with an empty password hash, so in that
        // configuration the message would hand out a password the database does not have.
        String message = authPlugin.notifyGeneratedPassword() ? core.getMessage("auto-register") : null;
        if (success && message != null) {
            message = message.replace("%password", generatedPassword);
            core.getPlugin().sendMessage(player, message);
        }

        return success;
    }

    public boolean forceLogin(P player) {
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Logging player {} in", getName(player));
        }

        boolean success = core.getAuthPluginHook().forceLogin(player);
        if (success) {
            core.sendLocaleMessage("auto-login", player);
        }

        return success;
    }

    public abstract FastLoginAutoLoginEvent callFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile);

    /** Whether the decision allowed platform-specific premium account handling. */
    protected boolean premiumHandlingAllowed;

    /** Explains the password flow; platforms may deduplicate against configure-time delivery. */
    protected void notifyPasswordLogin() {
        core.sendLocaleMessage("premium-password-login", player);
    }

    /** Explains why an existing premium account cannot use passwordless recovery. */
    protected void notifyPremiumPolicyDisabled() {
        core.sendLocaleMessage("premium-policy-disabled", player);
    }

    public abstract void onForceActionSuccess(LoginSession session);

    public abstract String getName(P player);

    public abstract boolean isOnline(P player);

    public abstract boolean isOnlineMode();

    /**
     * Refuses the connection because the verified identity and the account
     * ownership do not match.
     *
     * @param player the player to disconnect
     * @param message the localized reason
     */
    public abstract void denyLogin(P player, String message);

    /**
     * Reads the account state from the auth plugin. A read failure becomes
     * {@code READ_FAILURE}: the caller fails closed to the password lock and
     * logs it, so an unreadable account never gets passwordless access.
     *
     * @param authPlugin the auth plugin hook
     * @param playerName the player name
     * @return the account state, never null
     */
    private LoginMethodDecider.AccountState readAccountState(AuthPlugin<P> authPlugin, String playerName) {
        try {
            // the account state comes from the auth plugin itself, so a name can
            // never be handed to someone just because a premium identity claims it
            return authPlugin.inspectAccount(playerName);
        } catch (Exception readError) {
            core.getPlugin().getLog().warn(
                    "Could not read the account state of {}; keeping the password lock",
                    playerName, readError);
            return LoginMethodDecider.AccountState.READ_FAILURE;
        }
    }

    /**
     * The auto-login policy for this login: the proxy's declared value when it
     * declared one, otherwise this server's own key, which keeps older
     * proxies on the pre-decision behaviour.
     *
     * @param session the login session
     * @return whether premium auto-login is enabled
     */
    private boolean resolveAutoLogin(L session) {
        return LoginMethodDecider.resolvePolicyValue(session.getFacts().policyDeclared(),
                session.getFacts().autoLogin(), () -> (boolean) core.getConfig().get("autoLogin"));
    }

    /**
     * The auto-register policy for this login: the proxy's declared
     * value when it declared one, otherwise this server's own key. Gates
     * the "verified premium + no local account" cell — never an offline/cracked
     * connection.
     *
     * @param session the login session
     * @return whether verified identities without a local account are auto-registered
     */
    private boolean resolveAutoRegisterUnknown(L session) {
        return LoginMethodDecider.resolvePolicyValue(session.getFacts().policyDeclared(),
                session.getFacts().autoRegisterUnknown(),
                () -> (boolean) core.getConfig().get("auto-register-unknown"));
    }
}
