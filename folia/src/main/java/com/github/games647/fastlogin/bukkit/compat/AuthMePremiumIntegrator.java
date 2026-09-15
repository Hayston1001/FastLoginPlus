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
package com.github.games647.fastlogin.bukkit.compat;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import fr.xephi.authme.api.v3.AuthMeApi;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Integrates FastLoginPlus with AuthMe 6.0's premium system via reflection.
 *
 * <p>Provides three injection points:
 * <ul>
 *   <li>{@link #injectPendingPremium} — mark a player as pending premium verification</li>
 *   <li>{@link #injectVerifiedUuid} — store a verified Mojang UUID so AuthMe's
 *       {@code shouldSkipPreJoinDialogForPremium()} returns true</li>
 * </ul>
 *
 * <p>All methods are no-ops if AuthMe 6.0 is not detected.
 *
 * <p><b>How instances are found:</b> AuthMe 6.0 uses {@code ch.jalu.injector.Injector} as its DI
 * container. {@code PendingPremiumCache} and {@code PremiumLoginVerifier} are <em>not</em> fields
 * of {@code AuthMeApi} — they are separate beans managed by the injector and injected into
 * internal classes like {@code AsynchronousJoin} and {@code PaperDialogFlowListener}.
 * This class accesses them by reflecting on the {@code AuthMe.injector} field and calling
 * {@code injector.getSingleton(TargetClass.class)}.
 */
public final class AuthMePremiumIntegrator {

    /** AuthMe's Paper preJoin dialog listener — holds the pending response maps. */
    private static final String PAPER_DIALOG_LISTENER_CLASS =
        "fr.xephi.authme.listener.PaperDialogFlowListener";

    /**
     * AuthMe's proxy message receiver — one of the two components that cache
     * {@code enablePremium} in a field (ISS-11).
     *
     * <p>Package-private for the test suite, which pins that this name still resolves to an
     * AuthMe component implementing {@code SettingsDependent}: a rename upstream would turn the
     * refresh into a silent no-op, which is the very failure mode the refresh exists to remove.
     */
    static final String BUNGEE_RECEIVER_CLASS =
        "fr.xephi.authme.service.bungeecord.BungeeReceiver";

    /** The single parameter type of a {@code SettingsDependent}'s {@code reload} method. */
    private static final String AUTHME_SETTINGS_CLASS = "fr.xephi.authme.settings.Settings";

    private final FastLoginBukkit plugin;
    private final AuthMeVersionDetector versionDetector;

    // Cached reflection handles (lazy-initialized)
    private Object authMeInjector;
    private Object pendingPremiumCache;
    private Object premiumLoginVerifier;
    private Object dataSource;
    private Object playerCache;
    private Object bungeeSender;

    public AuthMePremiumIntegrator(FastLoginBukkit plugin, AuthMeVersionDetector versionDetector) {
        this.plugin = plugin;
        this.versionDetector = versionDetector;
    }

    /**
     * Returns true if AuthMe 6.0 is present AND enablePremium is true in AuthMe's config.
     * Reads enablePremium from AuthMe's config.yml via Bukkit plugin config.
     *
     * @return true if AuthMe 6.0 premium is enabled
     */
    public boolean isAuthMePremiumEnabled() {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        try {
            Plugin authMePlugin = Bukkit.getPluginManager().getPlugin("AuthMe");
            if (authMePlugin == null) {
                return false;
            }
            File authMeConfig = new File(authMePlugin.getDataFolder(), "config.yml");
            if (!authMeConfig.exists()) {
                return false;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(authMeConfig);
            return config.getBoolean("settings.enablePremium", false);
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Could not read AuthMe enablePremium: {}", e);
            }
            return false;
        }
    }



    /**
     * Inject a pending premium entry into AuthMe's PendingPremiumCache.
     * This causes {@code canBypassWithPremium()} to recognize the player's pending premium
     * enrollment on reconnect. No-op if AuthMe 6.0 is not present.
     *
     * @param playerName the player name
     * @param mojangUuid the verified Mojang UUID
     */
    public void injectPendingPremium(String playerName, UUID mojangUuid) {
        if (!versionDetector.isAuthMe6()) {
            return;
        }
        try {
            Object cache = getPendingPremiumCache();
            if (cache == null) {
                return;
            }

            Method addPending = cache.getClass().getMethod(
                "addPending", String.class, UUID.class);
            addPending.invoke(cache, playerName, mojangUuid);
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Injected pending premium for {} into AuthMe", playerName);
            }
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("PendingPremiumCache injection failed: {}", e);
            }
        }
    }

    /**
     * Store a verified Mojang UUID in AuthMe's PremiumLoginVerifier.
     * This enables {@code shouldSkipPreJoinDialogForPremium()} to find a verified session
     * (60s TTL) so the preJoin dialog is skipped for this player. No-op if AuthMe 6.0 is
     * not present.
     *
     * @param playerName the player name
     * @param mojangUuid the verified Mojang UUID
     */
    public void injectVerifiedUuid(String playerName, UUID mojangUuid) {
        if (!versionDetector.isAuthMe6()) {
            return;
        }
        try {
            Object verifier = getPremiumLoginVerifier();
            if (verifier == null) {
                return;
            }

            Method storeVerified = verifier.getClass().getMethod(
                "storeVerified", String.class, UUID.class);
            storeVerified.invoke(verifier, playerName, mojangUuid);
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Injected verified UUID for {} into AuthMe", playerName);
            }
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("PremiumLoginVerifier injection failed: {}", e);
            }
        }
    }

    /**
     * Directly set the premium UUID in AuthMe's database for the given player.
     * If the player has no AuthMe DB record (first login), pre-creates one
     * with the premium UUID already set so that the preJoin dialog is skipped.
     *
     * <p>A null {@code mojangUuid} is refused without touching the database — see
     * {@link #isStampablePremiumUuid(UUID)}.
     *
     * @param playerName the player name
     * @param mojangUuid the verified Mojang UUID (must not be null)
     * @return true if a new DB record was pre-created, false if an existing
     *         record was updated, the UUID was null, or the operation failed
     */
    public boolean markPlayerAsPremium(String playerName, UUID mojangUuid) {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        if (!isStampablePremiumUuid(mojangUuid)) {
            // ISS-04: callers can arrive here with null. 0.7.0/F10 narrowed the sources —
            // the proxy now sends its verified Mojang UUID in the force message and
            // BungeeListener adopts it — so null means "the proxy verified nothing": a
            // cracked login, Floodgate, a pre-0.7.0 proxy, or the ProtocolSupport path.
            // AuthMe derives isPremium() from premiumUuid != null, so writing null
            // would CLEAR the flag on an existing record rather than set it — and on
            // the first-login branch below it would create an AuthMe account with a
            // null UUID and an empty password hash. Refuse instead; the UUID-bearing
            // marking already ran during the configuration phase or direct
            // verification.
            plugin.getLog().warn(
                "Refusing to mark {} as premium in AuthMe: no verified UUID", playerName);
            return false;
        }
        try {
            Object injector = getAuthMeInjector();
            if (injector == null) {
                return false;
            }

            // Get DataSource from AuthMe's DI injector
            Class<?> dataSourceClass = Class.forName("fr.xephi.authme.datasource.DataSource");
            Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
            Object dataSource = getSingleton.invoke(injector, dataSourceClass);
            if (dataSource == null) {
                return false;
            }

            String lowerName = playerName.toLowerCase(java.util.Locale.ROOT);

            // Get the auth record
            Method getAuth = dataSource.getClass().getMethod("getAuth", String.class);
            Object auth = getAuth.invoke(dataSource, lowerName);
            if (auth == null) {
                // First login: no AuthMe DB record yet. Pre-create one with the
                // premium UUID so that shouldSkipPreJoinDialogForPremium() sees
                // auth.isPremium()=true during the configuration phase (which
                // runs BEFORE PlayerJoinEvent where forceRegister would normally
                // create the record). Without this, AuthMe shows a blocking
                // register dialog that the player must cancel before FLP can act.
                // 0.5.0/F060: propagate the failure — marking the session
                // registered on a record that was never created would make
                // ForceLoginTask forceLogin a non-existent AuthMe record and
                // leave the player unauthenticated
                return preCreatePremiumAuth(dataSource, lowerName, playerName, mojangUuid);
            }

            // Set premium UUID on existing record
            Method setPremiumUuid = auth.getClass().getMethod("setPremiumUuid", UUID.class);
            setPremiumUuid.invoke(auth, mojangUuid);

            // Update in database
            Method updatePremium = dataSource.getClass().getMethod(
                "updatePremiumUuid", auth.getClass());
            boolean success = (boolean) updatePremium.invoke(dataSource, auth);

            if (success) {
                // ISS-04: the UUID is logged because a success line reporting a null
                // write was exactly how this went unnoticed before — seeing the real
                // UUID here makes a regression visible in production logs.
                plugin.getLog().info(
                    "Marked {} as premium in AuthMe database (uuid={})", playerName, mojangUuid);
                // ISS-02 (reverse direction): keep the proxy's premium set in sync so
                // AuthMe's own premium path agrees with the database. Gated on a real
                // UUID — never advertise premium for a record we failed to stamp.
                if (mojangUuid != null) {
                    notifyProxyPremiumSet(playerName);
                }
            }
            return false;
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("markPlayerAsPremium failed: {}", e);
            }
            return false;
        }
    }

    /**
     * Clears all AuthMe state for a player when they switch to cracked mode.
     * For AuthMe 6.0: clears in-memory caches + DB premium flag + force-unregisters
     * FLP-created records.
     * For AuthMe 5.x: simply force-unregisters so the player can re-register with
     * their own password (the original password was randomly generated by FLP).
     *
     * <p>No-op if AuthMe is not installed.
     *
     * @param playerName the player name
     */
    public void clearPlayerPremium(String playerName) {
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("clearPlayerPremium called for: {}", playerName);
        }

        if (!versionDetector.isAuthMePresent()) {
            plugin.getLog().warn("clearPlayerPremium: AuthMe not present, aborting");
            return;
        }

        String lowerName = playerName.toLowerCase(java.util.Locale.ROOT);

        if (versionDetector.isAuthMe6()) {
            // AuthMe 6.0: clear caches + DB premium flag + forceUnregister.
            // Each step is independent — a failure in one does not skip the others.

            // 1. Clear PendingPremiumCache (5-minute TTL).
            try {
                Object cache = getPendingPremiumCache();
                if (cache != null) {
                    Method removePending = cache.getClass().getMethod("removePending", String.class);
                    removePending.invoke(cache, lowerName);
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info("Removed {} from AuthMe PendingPremiumCache", playerName);
                    }
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to clear AuthMe PendingPremiumCache for {}: {}",
                    playerName, e);
            }

            // 2. Clear PremiumLoginVerifier verified session (60-second TTL).
            try {
                Object verifier = getPremiumLoginVerifier();
                if (verifier != null) {
                    Field verifiedField = verifier.getClass().getDeclaredField("verified");
                    verifiedField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.concurrent.ConcurrentHashMap<String, Object> verifiedMap =
                        (java.util.concurrent.ConcurrentHashMap<String, Object>) verifiedField.get(verifier);
                    verifiedMap.remove(lowerName);
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info("Removed {} from AuthMe PremiumLoginVerifier", playerName);
                    }
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to clear AuthMe PremiumLoginVerifier for {}: {}",
                    playerName, e);
            }

            // 3. Delete the player's AuthMe database record, with cascade fallback.
            //    Tier 1: removeAuth() — delete entire record (best outcome).
            //    Tier 2: clear premium_uuid + reset password (if removeAuth fails
            //            but the record still exists). This ensures isPremium()=false
            //            so AuthMe's preJoin dialog won't be skipped, AND the player
            //            won't be locked out by an unknown password.
            //    We use direct reflection on DataSource instead of AuthMeApi because
            //    the AuthMe API dispatches asynchronously (Management.runTask() →
            //    BukkitService.runTaskOptionallyAsync()), causing a race with the kick.
            try {
                Object ds = getDataSource();
                if (ds == null) {
                    plugin.getLog().warn("Cannot clear AuthMe DB for {}: DataSource not available", playerName);
                } else {
                    // Tier 1: try to delete the entire record
                    Method removeAuth = ds.getClass().getMethod("removeAuth", String.class);
                    boolean removed = (boolean) removeAuth.invoke(ds, lowerName);
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info(
                            "AuthMe removeAuth({}) = {} (switched to cracked)", playerName, removed);
                    }

                    if (!removed) {
                        // Tier 2: record still exists — clear premium_uuid + reset password
                        Method getAuth = ds.getClass().getMethod("getAuth", String.class);
                        Object auth = getAuth.invoke(ds, lowerName);
                        if (auth != null) {
                            fallbackClearAuthMeRecord(ds, auth, playerName);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to unregister {} from AuthMe 6.0: {}",
                    playerName, e);
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("AuthMe unregister exception trace", e);
                }
            }

            // b) Remove from AuthMe's in-memory player cache (synchronous)
            // 0.5.0/F064: independent try-block — a DB failure must not skip the
            // cache cleanup (each step is independent, as the comment promises)
            try {
                Object pc = getPlayerCache();
                if (pc == null) {
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info("Cannot clear AuthMe PlayerCache for {}: not available", playerName);
                    }
                } else {
                    Method removePlayer = pc.getClass().getMethod("removePlayer", String.class);
                    removePlayer.invoke(pc, lowerName);
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info("Removed {} from AuthMe PlayerCache", playerName);
                    }
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to clear AuthMe PlayerCache for {}: {}",
                    playerName, e);
            }

            // ISS-02: the proxy keeps its own premium cache — the DB writes above bypass it.
            notifyProxyPremiumUnset(playerName);
        } else {
            clearPlayerPremiumLegacy5x(lowerName, playerName);
        }
    }

    /**
     * AuthMe 5.x cleanup path for {@link #clearPlayerPremium(String)}. That branch has no
     * premium feature and no caches, so the record is removed via direct DataSource
     * reflection with an async {@code AuthMeApi} fallback.
     *
     * @param lowerName  the lower-cased player name
     * @param playerName the player name (for logging)
     */
    private void clearPlayerPremiumLegacy5x(String lowerName, String playerName) {
        // Try synchronous reflection first (direct DataSource access),
        // fall back to async AuthMeApi if reflection fails.
        try {
            // Try to get DataSource from AuthMe plugin via reflection
            Plugin authMePlugin = Bukkit.getPluginManager().getPlugin("AuthMe");
            if (authMePlugin != null) {
                Field databaseField = authMePlugin.getClass().getDeclaredField("database");
                databaseField.setAccessible(true);
                Object ds = databaseField.get(authMePlugin);
                if (ds != null) {
                    Method removeAuth = ds.getClass().getMethod("removeAuth", String.class);
                    boolean removed = (boolean) removeAuth.invoke(ds, lowerName);
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info(
                            "AuthMe 5.x removeAuth({}) = {} (synchronous)", playerName, removed);
                    }
                    if (removed) {
                        return; // done synchronously
                    }
                }
            }
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info(
                "AuthMe 5.x synchronous cleanup failed for {}, falling back to async API: {}",
                playerName, e);
            }
        }

        // Fallback: async AuthMeApi
        try {
            AuthMeApi api = AuthMeApi.getInstance();
            if (api != null && api.isRegistered(lowerName)) {
                api.forceUnregister(lowerName);
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info(
                        "Unregistered {} from AuthMe 5.x (async fallback)", playerName);
                }
            }
        } catch (Exception e) {
            plugin.getLog().warn("Failed to unregister {} from AuthMe 5.x: {}",
                playerName, e);
        }
    }

    /**
     * Lightweight check called during cracked-session login to detect and clean
     * up stale AuthMe premium records left over from a failed
     * {@link #clearPlayerPremium(String)}. Only triggers full cleanup if the
     * AuthMe record still has {@code isPremium()=true} AND FLP's own profile
     * row exists (a genuine stale /cracked retry) — normal cracked players are
     * not affected.
     *
     * <p>When the AuthMe record is premium-flagged but FLP has no profile row
     * (DB reset, /flp delete or first login), the record is left in place
     * (fail-closed): deleting it would open a registration window for impostors
     * on the premium-verified name, while the surviving record keeps AuthMe's
     * own lockout for the legitimate owner.</p>
     *
     * @param playerName            the player name
     * @param existingCrackedPlayer whether FLP's own profile row exists for this
     *                              player (cracked-session path)
     */
    public void ensureNotPremium(String playerName, boolean existingCrackedPlayer) {
        if (!versionDetector.isAuthMe6()) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("ensureNotPremium: {} skipped (not AuthMe 6.0)", playerName);
            }
            return;
        }

        String lowerName = playerName.toLowerCase(java.util.Locale.ROOT);
        try {
            Object ds = getDataSource();
            if (ds == null) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("ensureNotPremium: {} skipped (DataSource null)", playerName);
                }
                return;
            }

            Method getAuth = ds.getClass().getMethod("getAuth", String.class);
            Object auth = getAuth.invoke(ds, lowerName);
            if (auth == null) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("ensureNotPremium: {} clean (no AuthMe record)", playerName);
                }
                return;
            }

            Method isPremium = auth.getClass().getMethod("isPremium");
            boolean premium = (boolean) isPremium.invoke(auth);
            if (!premium) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info(
                        "ensureNotPremium: {} clean (isPremium=false, normal cracked)", playerName);
                }
                return;
            }

            // premium-flagged record: only clean up when FLP's own profile row
            // still exists (stale /cracked retry).  A premium-flagged record
            // without an FLP row is a DB desync — deleting it would open a
            // registration window for impostors, so fail closed and warn.
            if (!shouldClearPremiumRecord(premium, existingCrackedPlayer)) {
                plugin.getLog().warn(
                    "ensureNotPremium: {} is premium-flagged in AuthMe but no FLP profile "
                        + "exists — possible DB desync; leaving record (fail-closed); "
                        + "admin should verify with /authme uuid other {}",
                    playerName, playerName);
                return;
            }

            // Stale premium record from a failed /cracked cleanup
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info(
                    "ensureNotPremium: {} STALE (isPremium=true) → triggering cleanup",
                    playerName);
            }
            clearPlayerPremium(playerName);
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info(
                "ensureNotPremium check failed for {}: {}", playerName, e);
            }
        }
    }

    /**
     * Decide whether the destructive AuthMe cleanup may run on the
     * cracked-session path (extracted as a pure function for testability).
     *
     * @param authMePremium         whether the AuthMe record is premium-flagged
     * @param existingCrackedPlayer whether FLP's own profile row exists for the player
     * @return true if the record is a stale premium entry that may be cleared
     */
    static boolean shouldClearPremiumRecord(boolean authMePremium, boolean existingCrackedPlayer) {
        return authMePremium && existingCrackedPlayer;
    }

    /**
     * Whether FLP may stamp AuthMe's {@code premium_uuid} column with the given UUID.
     * Extracted as a pure function for testability (mirrors
     * {@link #shouldClearPremiumRecord}).
     *
     * <p>AuthMe derives {@code isPremium()} from {@code premiumUuid != null}, so a null
     * write is not a no-op — it <em>clears</em> the premium flag on an existing record,
     * and on the first-login branch it would create an AuthMe account with a null UUID
     * and an empty password hash. Only a UUID verified against Mojang may be persisted.</p>
     *
     * <p><b>Version, not just nullness (ISS-25).</b> A non-null UUID is not sufficient: AuthMe
     * itself reads v3 as "name-derived, therefore unverified" and only treats v4 as
     * Mojang-issued. Stamping a v3 value makes
     * {@code AsynchronousJoin.canBypassWithPremium}'s v4 comparison miss permanently, so the
     * player silently loses premium auto-login until the record is repaired. Enforcing the
     * version here is what stops this gate from depending on every caller having already
     * filtered its input.</p>
     *
     * @param mojangUuid the UUID to persist, or null when the caller has none
     * @return true if the UUID may be written to AuthMe's database
     */
    static boolean isStampablePremiumUuid(UUID mojangUuid) {
        return mojangUuid != null && mojangUuid.version() == 4;
    }

    /**
     * Picks the UUID to stamp into AuthMe when the login session carries none.
     * Extracted as a pure function for testability (mirrors
     * {@link #isStampablePremiumUuid(UUID)}).
     *
     * <p>Historically the proxy LOGIN/REGISTER paths built their session without a UUID, so
     * {@code ForceLoginTask} had nothing to hand to
     * {@link #markPlayerAsPremium(String, UUID)}. On Paper that was masked by the
     * configuration phase, which stamps the record before the join; a Spigot backend has no
     * configuration phase and the proxy disables the ProtocolLib path, so nothing ever
     * marked the record — AuthMe kept treating a verified premium player as a plain offline
     * account, and FLP silently became a hard dependency: the player's AuthMe password was
     * generated at random by FLP, so losing FLP would lock them out.</p>
     *
     * <p>0.7.0/F10 closed the proxy half of that gap by carrying the verified UUID in the
     * force message, so this fallback fires less often. It still covers the sources F10
     * cannot reach: an older proxy, and the ProtocolSupport path (see ISS-29).</p>
     *
     * <p>The backend's own player UUID is the one the proxy forwarded, and its version
     * separates the two cases: <b>v4</b> is Mojang-issued (the proxy forwarded the verified
     * UUID), <b>v3</b> is name-derived and therefore offline — {@code premiumUuid:false}, a
     * cracked player, or a Floodgate account. AuthMe draws the same distinction in its own
     * {@code canBypassWithPremium}, so this is not a heuristic invented here.</p>
     *
     * <p><b>The session UUID gets the same treatment (ISS-25).</b> It used to be returned
     * unchecked, on the assumption that only direct verification and the Paper
     * configuration phase ever populate it. That assumption is what ISS-07 breaks: AuthMe's
     * Velocity premium handler rewrites {@code GameProfileRequestEvent}'s profile to the
     * offline UUID, and FLP's listener read that rewritten value — so a v3 session UUID is
     * reachable in production. Both sources now clear the same bar, which also removes this
     * method's dependence on which listener happens to run first.</p>
     *
     * @param sessionUuid    the UUID carried by the login session, null on the proxy paths
     * @param connectionUuid the joining player's UUID on this backend, null if unavailable
     * @return the UUID to stamp, or null when neither source can be trusted
     */
    public static UUID resolvePremiumUuid(UUID sessionUuid, UUID connectionUuid) {
        if (sessionUuid != null && sessionUuid.version() == 4) {
            return sessionUuid;
        }
        if (connectionUuid == null || connectionUuid.version() != 4) {
            return null;
        }
        return connectionUuid;
    }

    /**
     * What {@link #notifyProxyPremium} should do, decided from the two facts that only
     * become known after reflection has run.
     */
    enum ProxySyncDecision {
        /** Push the notification to the proxy. */
        SEND,
        /** Proxy configured, but nobody is online to carry the message — queue it for relay. */
        QUEUE,
        /** No proxy integration configured — nothing to keep in sync, stay silent. */
        SKIP,
        /** Sender unresolvable — a proxy may exist with a stale cache; warn the admin. */
        WARN
    }

    /**
     * Decides how a premium-state change should reach the AuthMe proxy. Extracted as a
     * pure function for testability (mirrors {@link #shouldClearPremiumRecord}).
     *
     * <p>The {@link ProxySyncDecision#SKIP} / {@link ProxySyncDecision#WARN} split carries
     * the load: a server with no proxy integration (direct-connect setups) must not log a
     * warning on every cracked toggle, while an unresolvable sender means the notification
     * silently did not happen — and on the cracked path that leaves the player forced into
     * online-mode and unable to join until the proxy resyncs.</p>
     *
     * <p>{@link ProxySyncDecision#QUEUE} is the fourth case: the proxy is configured, but no
     * player is online to carry the plugin message. AuthMe drops the notification in that
     * state — its {@code BungeeSender} needs a carrier connection — so it is queued for relay
     * instead of being sent into a void.</p>
     *
     * @param senderResolved   whether AuthMe's {@code BungeeSender} could be obtained
     * @param proxyEnabled     whether AuthMe reports its proxy integration as enabled
     * @param carrierAvailable whether a player is online to carry the plugin message
     * @return the action to take
     */
    static ProxySyncDecision decideProxySync(boolean senderResolved, boolean proxyEnabled,
                                             boolean carrierAvailable) {
        if (!senderResolved) {
            return ProxySyncDecision.WARN;
        }
        if (!proxyEnabled) {
            return ProxySyncDecision.SKIP;
        }
        return carrierAvailable ? ProxySyncDecision.SEND : ProxySyncDecision.QUEUE;
    }

    /**
     * Fallback for when {@link #clearPlayerPremium(String)} cannot delete the
     * AuthMe database record (removeAuth returns false). Clears the premium UUID
     * so that {@code isPremium()=false} (AuthMe's preJoin dialog will be shown)
     * and resets the password to an empty hash so the player can re-register.
     *
     * @param ds AuthMe's DataSource singleton
     * @param auth the PlayerAuth record (non-null)
     * @param playerName the player name (for logging)
     */
    private void fallbackClearAuthMeRecord(Object ds, Object auth, String playerName) throws Exception {
        plugin.getLog().warn(
            "AuthMe record for {} still exists after removeAuth returned false. "
            + "Falling back to clear premium flag + reset password.", playerName);

        // Clear premium UUID → isPremium() = false
        Method setPremiumUuid = auth.getClass().getMethod("setPremiumUuid", UUID.class);
        setPremiumUuid.invoke(auth, (UUID) null);
        Method updatePremium = ds.getClass().getMethod("updatePremiumUuid", auth.getClass());
        updatePremium.invoke(ds, auth);
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Cleared premium flag for {} in AuthMe (fallback)", playerName);
        }

        // Reset password to empty → player can re-register
        Class<?> hashedPwClass = Class.forName("fr.xephi.authme.security.crypts.HashedPassword");
        Object emptyHash = hashedPwClass.getConstructor(String.class).newInstance("");
        Method setPassword = auth.getClass().getMethod("setPassword", hashedPwClass);
        setPassword.invoke(auth, emptyHash);
        Method saveAuth = ds.getClass().getMethod("saveAuth", auth.getClass());
        saveAuth.invoke(ds, auth);
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Reset password for {} in AuthMe (fallback)", playerName);
        }
    }

    /**
     * Pre-creates a PlayerAuth record with the premium UUID already set,
     * so that AuthMe's preJoin dialog check sees isPremium()=true and
     * skips the blocking register dialog for first-time premium players.
     *
     * <p>Uses reflection to call DataSource.saveAuth(PlayerAuth). The
     * PlayerAuth is built with a empty hash (the player never needs
     * it — premium bypass skips password auth entirely) and the Mojang
     * UUID as both the premium UUID and the player UUID.
     *
     * @param dataSource AuthMe's DataSource singleton
     * @param lowerName lowercase player name (DB key)
     * @param playerName original-case player name (for realName field)
     * @param mojangUuid the verified Mojang UUID
     * @return true when the AuthMe record was created. Its premium UUID may still be
     *         missing — see {@link #persistPreCreatedPremium} — but the row exists either
     *         way, which is what the callers act on. False means no row was inserted.
     * @throws Exception on reflection or database failure
     */
    private boolean preCreatePremiumAuth(Object dataSource, String lowerName,
                                          String playerName, UUID mojangUuid) throws Exception {
        // Build: PlayerAuth.builder().name(lowerName).realName(playerName)
        //   .password(new HashedPassword("")).uuid(mojangUuid).premiumUuid(mojangUuid).build()
        Class<?> hashedPasswordClass = Class.forName(
            "fr.xephi.authme.security.crypts.HashedPassword");
        java.lang.reflect.Constructor<?> hpCtor =
            hashedPasswordClass.getConstructor(String.class);
        Object emptyPassword = hpCtor.newInstance("");

        Class<?> builderClass = Class.forName("fr.xephi.authme.data.auth.PlayerAuth$Builder");
        Object builder = Class.forName("fr.xephi.authme.data.auth.PlayerAuth")
            .getMethod("builder").invoke(null);

        Method nameMethod = builderClass.getMethod("name", String.class);
        nameMethod.invoke(builder, lowerName);

        Method realNameMethod = builderClass.getMethod("realName", String.class);
        realNameMethod.invoke(builder, playerName);

        Method passwordMethod = builderClass.getMethod("password", hashedPasswordClass);
        passwordMethod.invoke(builder, emptyPassword);

        Method uuidMethod = builderClass.getMethod("uuid", UUID.class);
        uuidMethod.invoke(builder, mojangUuid);

        Method premiumUuidMethod = builderClass.getMethod("premiumUuid", UUID.class);
        premiumUuidMethod.invoke(builder, mojangUuid);

        Method buildMethod = builderClass.getMethod("build");
        Object playerAuth = buildMethod.invoke(builder);

        Method saveAuth = dataSource.getClass().getMethod("saveAuth",
            Class.forName("fr.xephi.authme.data.auth.PlayerAuth"));
        boolean success = (boolean) saveAuth.invoke(dataSource, playerAuth);

        if (!success) {
            plugin.getLog().warn(
                "Failed to pre-create premium AuthMe record for {}", playerName);
            return false;
        }

        // saveAuth does NOT insert the premium_uuid column (AuthMe's AbstractSqlDataSource
        // only inserts NAME, NICK_NAME, PASSWORD, SALT, EMAIL, REGISTRATION_DATE,
        // REGISTRATION_IP, UUID), so the UUID needs a second write. That write reports a
        // failure by returning false rather than by throwing (AuthMeColumnsHandler swallows
        // the SQLException), which is why its result must not be discarded: the caller turns
        // it into "the player is registered", and a record without a premium UUID is not a
        // premium record.
        Method setPremiumUuid = playerAuth.getClass().getMethod("setPremiumUuid", UUID.class);
        setPremiumUuid.invoke(playerAuth, mojangUuid);
        Method updatePremium = dataSource.getClass().getMethod(
            "updatePremiumUuid", playerAuth.getClass());
        if (persistPreCreatedPremium(new PremiumStampOps() {

            @Override
            public boolean stampPremiumUuid() {
                try {
                    return (boolean) updatePremium.invoke(dataSource, playerAuth);
                } catch (Exception e) {
                    // Convert, don't swallow: an AuthMe API change (reflection failure) and
                    // a transient DB error both arrive here, and the failure log below has
                    // to let an admin tell which one happened.
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info(
                            "AuthMe premium_uuid write for {} threw: {}", playerName, e);
                    }
                    return false;
                }
            }
        })) {
            plugin.getLog().info(
                "Pre-created premium AuthMe record for {} (uuid={})", playerName, mojangUuid);
            // ISS-02 (reverse direction): the record is premium — tell the proxy so its
            // premium set matches the database instead of waiting for the next resync.
            // Only after a confirmed stamp: advertising premium for a row we failed to
            // stamp is exactly the drift this class exists to prevent.
            if (mojangUuid != null) {
                notifyProxyPremiumSet(playerName);
            }
        } else {
            // The row exists but is not a premium record. AuthMe reads a null premium_uuid
            // as "not premium", so the player is treated as a cracked account whose
            // password hash is empty — they cannot answer the login dialog it triggers,
            // and the session was already started as registered. The next login replays
            // this path and retries the write, so a transient fault repairs itself.
            plugin.getLog().error(
                "Created the AuthMe record for {} but could not persist its premium UUID. "
                + "The player counts as non-premium until that write succeeds; the next "
                + "login retries it. If this repeats, check the database connection and "
                + "AuthMe's schema.", playerName);
        }
        return true;
    }

    /**
     * The premium-UUID write performed right after a record is created. Injected so the
     * retry below can be tested without an AuthMe datasource.
     */
    interface PremiumStampOps {

        /**
         * Attempts to write the premium UUID onto the row that was just created.
         *
         * @return true when AuthMe reports the write as successful
         */
        boolean stampPremiumUuid();
    }

    /**
     * Persists the premium UUID of a freshly pre-created AuthMe record, retrying once
     * (ISS-18).
     *
     * <p>AuthMe reports a failed {@code updatePremiumUuid} by returning false — the
     * underlying {@code AuthMeColumnsHandler} catches the {@code SQLException} and only
     * logs it — so discarding that return value leaves the caller believing it created a
     * premium record while the row holds no premium UUID. AuthMe reads that null as "not
     * premium", so the player faces a login dialog for an account whose password hash is
     * empty, which nobody can satisfy.</p>
     *
     * <p>One retry is attempted, because the failures this guards against (SQLite busy, a
     * dropped MySQL connection) are transient and the write is idempotent — the UUID
     * already sits on the in-memory {@code PlayerAuth}.</p>
     *
     * <p><b>The record is deliberately not rolled back when the write fails.</b> Three
     * things rule the deletion out; the second is what decided it:</p>
     * <ul>
     *   <li>{@code DataSource.removeAuth} reports success for any DELETE that runs without
     *       raising, matched row or not — AuthMe's SQLite, MySQL and PostgreSQL
     *       implementations all discard the update count ({@code pst.executeUpdate();
     *       return true;}) — so "the row is gone" cannot be established without a read-back
     *       this path does not perform.</li>
     *   <li>Callers take this method's {@code true} to mean "an AuthMe record now exists".
     *       {@code FastLoginBukkit.applyPremiumAtConfigure} discards the result and
     *       hard-codes {@code registered=true}, so a rollback would send it into
     *       {@code forceLogin} against a row that is gone: AuthMe then does nothing and the
     *       player is left unauthenticated while the proxy is told the action succeeded.</li>
     *   <li>Deleting is irreversible and buys little — both alternative end states (a row
     *       with an empty password hash, a fresh registration whose password FLP generated
     *       and never told the player) are equally unusable for a password login.</li>
     * </ul>
     *
     * <p>Leaving the row in place also keeps this method's return value meaning exactly what
     * it meant before the fix, so no caller has to change.</p>
     *
     * @param ops the write, injected so the retry can be tested without a datasource
     * @return true when AuthMe reports the premium UUID as persisted
     */
    static boolean persistPreCreatedPremium(PremiumStampOps ops) {
        if (ops.stampPremiumUuid()) {
            return true;
        }
        // One retry: the failure modes here are transient and the write is idempotent, so a
        // second attempt costs nothing and usually succeeds. Its result is the one returned.
        return ops.stampPremiumUuid();
    }

    /**
     * Notifies the AuthMe proxy (BungeeCord/Velocity) that the given player is no longer
     * premium, so the proxy drops the name from its premium cache.
     *
     * <p>The proxy keeps its own premium name set — fed only by
     * {@code premium.set}/{@code premium.unset} messages or a full resync at proxy start —
     * and drives {@code forceOnlineMode()} from it. Writing AuthMe's database directly (as
     * this class otherwise does) leaves that set untouched, so a player switched to cracked
     * keeps being rejected at connect time until the proxy restarts with a player online to
     * trigger the resync.</p>
     *
     * @param playerName the player name
     * @return true if the notification was handed to AuthMe; false if it was queued
     * for relay or could not be sent
     */
    public boolean notifyProxyPremiumUnset(String playerName) {
        return notifyProxyPremium(playerName, "sendPremiumUnset", "unset", false);
    }

    /**
     * Notifies the AuthMe proxy that the given player is now premium, so the proxy adds the
     * name to its premium cache and forces online-mode verification on the next connection.
     * Counterpart to {@link #notifyProxyPremiumUnset(String)}.
     *
     * @param playerName the player name
     * @return true if the notification was handed to AuthMe; false if it was queued
     * for relay or could not be sent
     */
    public boolean notifyProxyPremiumSet(String playerName) {
        return notifyProxyPremium(playerName, "sendPremiumSet", "set", true);
    }

    /**
     * Resolves AuthMe's {@code BungeeSender} and invokes the given premium notification on
     * it. Silently no-ops when AuthMe's proxy integration is disabled (there is no remote
     * cache to update) and warns when the sender cannot be resolved at all.
     *
     * <p>AuthMe can only deliver this message while a player is online to carry it; its
     * {@code BungeeSender} picks one and otherwise drops the notification after a warning of
     * its own. Instead of sending into that void, the notification is queued and relayed once
     * anybody reaches the play phase. This matters most for {@code premium.unset}: the proxy's
     * premium set drives {@code forceOnlineMode()}, so losing that direction locks a demoted
     * player out until the proxy restarts.</p>
     *
     * @param playerName the player name
     * @param methodName the {@code BungeeSender} method to invoke
     * @param action     short action label used in log messages
     * @param isSet      true for {@code premium.set}, false for {@code premium.unset}
     * @return true if the notification was handed to AuthMe
     */
    private boolean notifyProxyPremium(String playerName, String methodName, String action,
                                       boolean isSet) {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        try {
            Object sender = getBungeeSender();
            boolean proxyEnabled = false;
            if (sender != null) {
                Method isEnabled = sender.getClass().getMethod("isEnabled");
                proxyEnabled = Boolean.TRUE.equals(isEnabled.invoke(sender));
            }

            // AuthMe picks its carrier from the very same list (its BukkitService delegates
            // straight to Bukkit#getOnlinePlayers), so this probe cannot count a player that
            // AuthMe would not. BungeeManager is the second gate: AuthMe's bungeecord hook
            // being on does not prove a proxy is in front of this server, and queueing for a
            // proxy that never answers would only rotate the entry forever.
            boolean carrierAvailable = proxyEnabled
                    && plugin.getBungeeManager().isEnabled()
                    && !Bukkit.getOnlinePlayers().isEmpty();

            switch (decideProxySync(sender != null, proxyEnabled, carrierAvailable)) {
                case WARN:
                    warnProxyCacheStale(playerName, action);
                    return false;
                case SKIP:
                    // No proxy integration configured — nothing to keep in sync.
                    return false;
                case QUEUE:
                    if (plugin.getPendingRelayStore().queuePremiumNotice(playerName, isSet)) {
                        // schedule a retry only for a newly queued entry — an entry already
                        // waiting has a live retry task, which reads the latest state at
                        // send time
                        plugin.schedulePremiumRelay(playerName);
                        plugin.getLog().info("Queued AuthMe premium.{} for {} — no online player "
                                + "available as message carrier; relaying once one is",
                            action, playerName);
                    }
                    return false;
                default:
                    Method notify = sender.getClass().getMethod(methodName, String.class);
                    notify.invoke(sender, playerName);
                    // a direct send supersedes anything still queued for the same player
                    plugin.getPendingRelayStore().removePremiumNotice(playerName);
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info("Sent premium.{} notification to proxy for {}",
                            action, playerName);
                    }
                    return true;
            }
        } catch (Exception e) {
            warnProxyCacheStale(playerName, action);
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("notifyProxyPremium({}) failed: {}", action, e);
            }
            return false;
        }
    }

    /**
     * Warns that the proxy's premium cache could not be updated. The cracked direction is
     * the dangerous one: a stale entry makes the proxy force online-mode for a player who
     * is no longer premium, locking them out until the proxy resyncs.
     *
     * @param playerName the player name
     * @param action     short action label used in the message
     */
    private void warnProxyCacheStale(String playerName, String action) {
        plugin.getLog().warn(
            "Could not send the AuthMe premium.{} message for {}. If AuthMe runs on both the "
                + "proxy and the backend, the proxy premium cache is now stale: a player switched "
                + "to cracked stays forced into online-mode and cannot join until the proxy "
                + "restarts with a player online to trigger the resync.",
            action, playerName);
    }

    // --- Reflection helpers ---

    /**
     * Returns AuthMe's {@code ch.jalu.injector.Injector} instance by reflecting on the
     * {@code AuthMe.injector} field. Cached after first successful lookup.
     *
     * @return the injector instance, or null if not found
     */
    private synchronized Object getAuthMeInjector() throws Exception {
        if (authMeInjector != null) {
            return authMeInjector;
        }
        Plugin authMePlugin = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMePlugin == null) {
            return null;
        }
        // AuthMe has: private Injector injector;
        for (Class<?> c = authMePlugin.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getName().equals("ch.jalu.injector.Injector")) {
                    f.setAccessible(true);
                    authMeInjector = f.get(authMePlugin);
                    return authMeInjector;
                }
            }
        }
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Could not find AuthMe's Injector field");
        }
        return null;
    }

    /**
     * Gets the {@code PendingPremiumCache} singleton from AuthMe's DI injector.
     * The injector's {@code getSingleton()} method returns the managed instance.
     *
     * @return the PendingPremiumCache instance, or null if not found
     */
    private synchronized Object getPendingPremiumCache() throws Exception {
        if (pendingPremiumCache != null) {
            return pendingPremiumCache;
        }
        Object injector = getAuthMeInjector();
        if (injector == null) {
            return null;
        }
        Class<?> cacheClass = Class.forName("fr.xephi.authme.service.PendingPremiumCache");
        Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
        pendingPremiumCache = getSingleton.invoke(injector, cacheClass);
        return pendingPremiumCache;
    }

    /**
     * Gets the {@code PremiumLoginVerifier} singleton from AuthMe's DI injector.
     * The injector's {@code getSingleton()} method returns the managed instance.
     *
     * @return the PremiumLoginVerifier instance, or null if not found
     */
    private synchronized Object getPremiumLoginVerifier() throws Exception {
        if (premiumLoginVerifier != null) {
            return premiumLoginVerifier;
        }
        Object injector = getAuthMeInjector();
        if (injector == null) {
            return null;
        }
        Class<?> verifierClass = Class.forName("fr.xephi.authme.service.PremiumLoginVerifier");
        Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
        premiumLoginVerifier = getSingleton.invoke(injector, verifierClass);
        return premiumLoginVerifier;
    }

    /**
     * Gets the {@code DataSource} singleton from AuthMe's DI injector.
     *
     * @return the DataSource instance, or null if not found
     */
    private synchronized Object getDataSource() throws Exception {
        if (dataSource != null) {
            return dataSource;
        }
        Object injector = getAuthMeInjector();
        if (injector == null) {
            return null;
        }
        Class<?> dsClass = Class.forName("fr.xephi.authme.datasource.DataSource");
        Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
        dataSource = getSingleton.invoke(injector, dsClass);
        return dataSource;
    }

    /**
     * Gets the {@code BungeeSender} singleton from AuthMe's DI injector. This is the
     * same instance AuthMe's own {@code PremiumService} uses, so invoking its
     * notification methods produces exactly the plugin messages AuthMe itself would
     * send — no direct database writes, no AuthMe command feedback, no player kick.
     *
     * @return the BungeeSender instance, or null if not found
     */
    private synchronized Object getBungeeSender() throws Exception {
        if (bungeeSender != null) {
            return bungeeSender;
        }
        Object injector = getAuthMeInjector();
        if (injector == null) {
            return null;
        }
        Class<?> senderClass = Class.forName("fr.xephi.authme.service.bungeecord.BungeeSender");
        Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
        bungeeSender = getSingleton.invoke(injector, senderClass);
        return bungeeSender;
    }

    /**
     * Gets the {@code PlayerCache} singleton from AuthMe's DI injector.
     *
     * @return the PlayerCache instance, or null if not found
     */
    private Object getPlayerCache() throws Exception {
        if (playerCache != null) {
            return playerCache;
        }
        Object injector = getAuthMeInjector();
        if (injector == null) {
            return null;
        }
        Class<?> cacheClass = Class.forName("fr.xephi.authme.data.auth.PlayerCache");
        Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
        playerCache = getSingleton.invoke(injector, cacheClass);
        return playerCache;
    }

    /**
     * Forces AuthMe's {@code settings.enablePremium} to {@code true}.
     *
     * <p>This is required because AuthMe 6.0 short-circuits ALL premium checks
     * (preJoin dialog skip, canBypassWithPremium, PremiumService) when
     * {@code enablePremium=false}. FLP does the actual Mojang verification via
     * ProtocolLib, but AuthMe's checks must still be "unlocked" for FLP's
     * injected state to be read.
     *
     * <p>Implementation:
     * <ol>
     *   <li>Modify AuthMe's config.yml on disk (persisted across restarts)</li>
     *   <li>Call Settings.setProperty(ENABLE_PREMIUM, true) to update memory</li>
     *   <li>Call Settings.save() to persist to disk</li>
     *   <li>Refresh every component that caches {@code enablePremium} in a field
     *       (PacketEventsService and BungeeReceiver) — see
     *       {@link #reloadEnablePremiumConsumers}</li>
     * </ol>
     *
     * @return true if the operation succeeded
     */
    public boolean forceEnablePremium() {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        try {
            Object injector = getAuthMeInjector();
            if (injector == null) {
                // 0.5.0/F061: a silent failure here leaves the whole 6.0
                // integration dead without any signal — warn loudly
                plugin.getLog().warn("AuthMe injector not found — cannot enforce"
                        + " FastLogin premium control (is the AuthMe version supported?)");
                return false;
            }

            // 1. Get Settings singleton from AuthMe's DI injector
            Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
            Class<?> settingsClass = Class.forName("fr.xephi.authme.settings.Settings");
            Object settings = getSingleton.invoke(injector, settingsClass);
            if (settings == null) {
                return false;
            }

            // 2. Check current value
            Class<?> premiumSettingsClass = Class.forName(
                "fr.xephi.authme.settings.properties.PremiumSettings");
            Field enablePremiumField = premiumSettingsClass.getField("ENABLE_PREMIUM");
            Object enablePremiumProperty = enablePremiumField.get(null);

            Method getProperty = settings.getClass().getMethod("getProperty",
                Class.forName("ch.jalu.configme.properties.Property"));
            boolean currentValue = (boolean) getProperty.invoke(settings, enablePremiumProperty);

            if (currentValue) {
                // Already enabled — nothing to do
                return true;
            }

            // 3. setProperty + save (persist to disk so /authme reload keeps it)
            Method setProperty = settings.getClass().getMethod("setProperty",
                Class.forName("ch.jalu.configme.properties.Property"), Object.class);
            setProperty.invoke(settings, enablePremiumProperty, true);

            Method save = settings.getClass().getMethod("save");
            save.invoke(settings);

            plugin.getLog().info(
                "FLP has forced AuthMe's enablePremium=true (was false). "
                + "FLP handles Mojang verification; AuthMe's premium checks are now unlocked.");

            // 4. Refresh every component that caches enablePremium, so the forced value takes
            //    effect without a restart or a manual /authme reload
            reloadEnablePremiumConsumers(injector, settings);

            return true;
        } catch (Exception e) {
            plugin.getLog().error("Failed to force-enable AuthMe enablePremium: {}", e);
            return false;
        }
    }

    /**
     * Refreshes the components that cache {@code enablePremium} in a field, so the value FLP has
     * just forced in memory takes effect.
     *
     * <p>AuthMe's {@code SettingsDependent} contract is "classes that keep a local copy of certain
     * settings" — such a component picks up a new value only when {@code reload(Settings)} is called
     * on it. Exactly two components cache this one (checked against AuthMe 6.0.1 by following every
     * {@code ENABLE_PREMIUM} reference; the other seven read it on each use):
     * {@code PacketEventsService} and {@code BungeeReceiver}. A stale {@code BungeeReceiver} answers
     * the next {@code proxy.started} with an empty premium list, which wipes the proxy's cache of
     * verified premium players (ISS-11).
     *
     * <p>Deliberately not a full {@code SettingsDependent} refresh (what AuthMe's own
     * {@code /authme reload} performs): that would call {@code reload()} on twelve further
     * components to fix one known stale field.
     *
     * @param injector AuthMe's DI injector
     * @param settings AuthMe's Settings singleton
     * @throws Exception when PacketEventsService cannot be reloaded
     */
    private void reloadEnablePremiumConsumers(Object injector, Object settings) throws Exception {
        // unchanged from before this fix: a failure here still fails the whole force
        reloadPacketEventsService(injector, settings);

        // ISS-11: this one must only warn on failure — see refreshCachedEnablePremium
        refreshCachedEnablePremium(BUNGEE_RECEIVER_CLASS, AUTHME_SETTINGS_CLASS, injector, settings,
            failure -> plugin.getLog().warn("Could not refresh AuthMe's BungeeReceiver after forcing"
                + " enablePremium — the proxy's premium list may be wiped on the next"
                + " proxy.started message (ISS-11): {}", failure));
    }

    /**
     * Refreshes one AuthMe component that caches {@code enablePremium} in a field.
     *
     * <p>The two failure kinds are kept apart on purpose. A component this AuthMe build does not
     * ship — the class is looked up by name — is normal and silent: not every build has every
     * component, and warning on it would fire on every startup of such a build. A component that
     * exists but cannot be reloaded is handed to {@code warnOnFailure}. Neither is propagated:
     * a throw here would reach {@code forceEnablePremium}'s catch, which turns any exception into a
     * {@code false} return and marks the whole AuthMe integration as failed — a disproportionate
     * answer to a possibly stale proxy premium list.
     *
     * <p>The two class names are parameters rather than constants inside this method so that the
     * refresh can be exercised against a stand-in component — the same reason
     * {@link #persistPreCreatedPremium} takes its write as a parameter.
     *
     * @param consumerClassName the AuthMe component's class name
     * @param settingsClassName the settings class its {@code reload} method takes as its argument
     * @param injector          AuthMe's DI injector
     * @param settings          AuthMe's Settings singleton
     * @param warnOnFailure     receives the cause when the component exists but cannot be reloaded
     * @return true when the component was reloaded
     */
    static boolean refreshCachedEnablePremium(String consumerClassName, String settingsClassName,
            Object injector, Object settings, Consumer<Exception> warnOnFailure) {
        try {
            Class<?> consumerClass = Class.forName(consumerClassName);
            Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
            Object consumer = getSingleton.invoke(injector, consumerClass);
            if (consumer == null) {
                // the injector never created this component — nothing to refresh
                return false;
            }
            Method reload = consumerClass.getMethod("reload", Class.forName(settingsClassName));
            reload.invoke(consumer, settings);
            return true;
        } catch (ClassNotFoundException e) {
            // this AuthMe build has no such component — normal, not a failure
            return false;
        } catch (Exception e) {
            warnOnFailure.accept(e);
            return false;
        }
    }

    /**
     * Removes AuthMe's {@code PremiumVerificationPacketListener} so FLP's ProtocolLib listener is
     * the sole packet-level Mojang verification — and leaves AuthMe itself believing the listener
     * is still registered, so it does not put it back.
     *
     * <p>When {@code enablePremium=true}, AuthMe registers its own PacketEvents listener that
     * intercepts START/ENCRYPTION_RESPONSE packets. This conflicts with FLP's ProtocolLib listener
     * which does the same thing. FLP does the actual verification and injects results into AuthMe's
     * internal state (PendingPremiumCache, PremiumLoginVerifier), so AuthMe's own listener is
     * redundant and must be removed.
     *
     * <p><b>Why the flag is left at {@code true} (ISS-32).</b> AuthMe decides whether to
     * (re-)register that listener in {@code PacketEventsService.setup()}: when premium packet
     * verification is needed it registers only {@code if (!premiumVerificationRegistered)}. That
     * method runs again on every {@code /authme reload} (through {@code reload(settings)}) and
     * again whenever the {@code packetevents} plugin is enabled. Writing {@code false} after
     * removing the listener therefore invites AuthMe to re-register it on the next reload, which
     * silently restores the double interception this method exists to remove — and leaves the
     * admin's "configuration reloaded" looking trustworthy while it is not. Leaving the flag
     * {@code true} makes {@code setup()} skip the registration instead, so the takeover survives
     * a reload. Across AuthMe 6.0.0 and 6.0.1 (byte-identical file) the field appears solely in
     * {@code setup()} and {@code disable()}, never in a business rule, so this only affects the
     * register/skip decision.
     *
     * <p>Two upstream paths still reset the flag — {@code disable()} when {@code packetevents} is
     * unloaded, and {@code setup()}'s else-branch when {@code enablePremium} turns false — so this
     * method stays idempotent and cheap to call again: it is the re-assert primitive used by the
     * event hooks in {@code AuthMeTakeoverListener} and by the login-time fallback.
     *
     * <p>Uses AuthMe's public {@code PacketInterceptionAdapter.unregisterPremiumVerification()}
     * method — the same method AuthMe itself uses to clean up the listener — whose implementation
     * null-guards the listener, so calling it without a registered listener is a no-op rather than
     * an error. Deliberately silent: the callers decide what the event they reacted to deserves to
     * be logged, because this method can no longer tell "removed one" from "nothing to remove".
     *
     * @return true when AuthMe's object graph was driven successfully; false when the listener
     *         could not be removed (it may still be registered)
     */
    public boolean unregisterPremiumPacketListener() {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        try {
            Object injector = getAuthMeInjector();
            if (injector == null) {
                return false;
            }

            // Get PacketInterceptionAdapter (implemented by the platform adapter, which
            // delegates to PacketEventsListenerRegistry)
            Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
            Class<?> adapterClass = Class.forName(
                "fr.xephi.authme.platform.PacketInterceptionAdapter");
            Object adapter = getSingleton.invoke(injector, adapterClass);
            if (adapter == null) {
                return false;
            }

            // Call unregisterPremiumVerification() on the adapter. Upstream null-guards the
            // listener, so this is a no-op when there is nothing registered (no PacketEvents,
            // proxy mode, or a previous call already removed it).
            Method unregister = adapterClass.getMethod("unregisterPremiumVerification");
            unregister.invoke(adapter);

            // ISS-32: leave AuthMe's flag at "registered" so its own reload path
            // (PacketEventsService.setup()) skips re-registration — see the javadoc
            Class<?> pesClass = Class.forName(
                "fr.xephi.authme.listener.packetevents.PacketEventsService");
            Object pes = getSingleton.invoke(injector, pesClass);
            if (pes != null) {
                Field registeredField = pesClass.getDeclaredField("premiumVerificationRegistered");
                registeredField.setAccessible(true);
                registeredField.setBoolean(pes, true);
            }

            return true;
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Failed to unregister premium packet listener: {}", e);
            }
            return false;
        }
    }

    /**
     * Performs the full AuthMe 6.0 takeover: forces enablePremium=true and
     * unregisters AuthMe's redundant packet listener. Called once at startup after
     * AuthMe has fully initialized; the individual halves are what later re-assertions
     * use (see {@link #unregisterPremiumPacketListener()}).
     *
     * @return true if both operations succeeded
     */
    public boolean enforceFlpPremiumControl() {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        boolean forced = forceEnablePremium();
        boolean unregistered = unregisterPremiumPacketListener();
        if (forced && !unregistered) {
            // 0.5.0/F061: half-applied state — enablePremium was persisted but
            // AuthMe's packet listener may still be registered, which would
            // double-intercept START/ENCRYPTION_RESPONSE
            plugin.getLog().warn("enablePremium was set but AuthMe's premium packet"
                    + " listener could not be unregistered — verify no double"
                    + " interception of login packets occurs");
        }
        return forced && unregistered;
    }

    /**
     * Reloads AuthMe's PacketEventsService so it re-evaluates listener
     * registration with the updated enablePremium setting.
     *
     * @param injector AuthMe's DI injector
     * @param settings AuthMe's Settings singleton
     */
    private void reloadPacketEventsService(Object injector, Object settings) throws Exception {
        Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
        Class<?> pesClass = Class.forName(
            "fr.xephi.authme.listener.packetevents.PacketEventsService");
        Object pes = getSingleton.invoke(injector, pesClass);
        if (pes == null) {
            return;
        }
        // Call reload(settings) which re-reads enablePremium and calls setup()
        Method reload = pesClass.getMethod("reload",
            Class.forName("fr.xephi.authme.settings.Settings"));
        reload.invoke(pes, settings);
    }

    /**
     * Closes AuthMe 6.0's blocking preJoin registration dialog for the given
     * player by completing the pending register response CompletableFuture.
     *
     * @param playerId   the player's connection UUID (v3 or v4, as assigned by Paper)
     * @param connection the Paper {@code PlayerConfigurationConnection} of this login
     *                   attempt, or null when unavailable — used to resolve AuthMe
     *                   6.0.1's per-connection dialog session id
     */
    public void closePreJoinRegisterDialog(UUID playerId, Object connection) {
        if (!versionDetector.isAuthMe6()) {
            return;
        }
        try {
            Object dialogListener = getPaperDialogFlowListener();
            if (dialogListener == null) {
                return;
            }
            if (completePreJoinDialog(dialogListener, "pendingRegisterResponses",
                    connection, playerId)) {
                plugin.getLog().info("Closed AuthMe preJoin register dialog for {}", playerId);
            }
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Failed to close AuthMe preJoin dialog: {}", e);
            }
        }
    }

    /**
     * Closes AuthMe 6.0's blocking preJoin login dialog for the given player.
     * This is the LOGIN dialog (for existing records with password), not the
     * REGISTER dialog.  Needed when a cracked→premium toggle creates a record
     * that AuthMe's HIGHEST handler sees as non-premium (async timing race)
     * and shows a login dialog.
     *
     * @param playerId   the player's connection UUID (v3 or v4, as assigned by Paper)
     * @param connection the Paper {@code PlayerConfigurationConnection} of this login
     *                   attempt, or null when unavailable — used to resolve AuthMe
     *                   6.0.1's per-connection dialog session id
     */
    public void closePreJoinLoginDialog(UUID playerId, Object connection) {
        if (!versionDetector.isAuthMe6()) {
            return;
        }
        try {
            Object dialogListener = getPaperDialogFlowListener();
            if (dialogListener == null) {
                return;
            }
            if (completePreJoinDialog(dialogListener, "pendingLoginResponses",
                    connection, playerId)) {
                plugin.getLog().info("Closed AuthMe preJoin login dialog for {}", playerId);
            }
        } catch (Exception e) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Failed to close AuthMe preJoin dialog: {}", e);
            }
        }
    }

    /**
     * Resolves AuthMe's {@code PaperDialogFlowListener} singleton, which holds
     * the pending preJoin dialog responses.
     *
     * @return the listener instance, or null when AuthMe is not injected yet
     * @throws Exception when AuthMe's injector cannot be queried
     */
    private Object getPaperDialogFlowListener() throws Exception {
        Object injector = getAuthMeInjector();
        if (injector == null) {
            return null;
        }
        Class<?> dialogListenerClass = Class.forName(PAPER_DIALOG_LISTENER_CLASS);
        Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
        return getSingleton.invoke(injector, dialogListenerClass);
    }

    /**
     * Completes the pending preJoin dialog future registered for the given connection.
     *
     * <p>AuthMe 6.0.1 changed the key type of {@code pendingLoginResponses} and
     * {@code pendingRegisterResponses} from the player {@link UUID} to a {@code Long}
     * session id.  The session id lives in the separate {@code connectionSessions}
     * map, which is keyed by the connection object.  Java erases generics at runtime,
     * so the previous {@code Map<UUID, ...>} lookup still compiled and still ran — it
     * merely always returned null, leaving the dialog open until AuthMe's own timeout
     * fired, with no exception and no log line to show for it.
     *
     * <p>Both key shapes are tried instead of branching on a version number: a version
     * check needs a reliable way to read AuthMe's version and would silently break
     * again on the next refactor, whereas a lookup miss is harmless —
     * {@code ConcurrentHashMap.get} returns null for a key of an unrelated type.
     * When neither key matches, nothing happens, which is the pre-fix behaviour.
     *
     * <p>The register dialog cannot go through AuthMe's own
     * {@code approvePreJoinForceLogin}: {@code handleBlockingRegisterDialog} never
     * registers its future with {@code PreJoinDialogService}, only the login dialog
     * does, so that method cannot reach it.
     *
     * @param dialogListener AuthMe's {@code PaperDialogFlowListener} singleton
     * @param fieldName      {@code pendingRegisterResponses} or {@code pendingLoginResponses}
     * @param connection     the Paper connection object, or null
     * @param playerId       the player's connection UUID
     * @return true when a pending future was found and completed
     * @throws Exception when the response map cannot be read reflectively
     */
    private boolean completePreJoinDialog(Object dialogListener, String fieldName,
            Object connection, UUID playerId) throws Exception {
        Object matchedKey = completePendingDialog(Class.forName(PAPER_DIALOG_LISTENER_CLASS),
            dialogListener, fieldName, connection, playerId);
        if (matchedKey == null) {
            return false;
        }
        if (matchedKey instanceof Long && plugin.getCore().isDebug()) {
            plugin.getLog().info("Closed AuthMe {} via session id {}", fieldName, matchedKey);
        }
        return true;
    }

    /**
     * Completes the pending preJoin dialog future registered for the given connection.
     *
     * <p>AuthMe 6.0.1 changed the key type of {@code pendingLoginResponses} and
     * {@code pendingRegisterResponses} from the player {@link UUID} to a {@code Long}
     * session id.  The session id lives in the separate {@code connectionSessions}
     * map, which is keyed by the connection object.  Java erases generics at runtime,
     * so the previous {@code Map<UUID, ...>} lookup still compiled and still ran — it
     * merely always returned null, leaving the dialog open until AuthMe's own timeout
     * fired, with no exception and no log line to show for it.
     *
     * <p>Both key shapes are tried instead of branching on a version number: a version
     * check needs a reliable way to read AuthMe's version and would silently break
     * again on the next refactor, whereas a lookup miss is harmless —
     * {@code ConcurrentHashMap.get} returns null for a key of an unrelated type.
     * When neither key matches, nothing happens, which is the pre-fix behaviour.
     *
     * <p>The register dialog cannot go through AuthMe's own
     * {@code approvePreJoinForceLogin}: {@code handleBlockingRegisterDialog} never
     * registers its future with {@code PreJoinDialogService}, only the login dialog
     * does, so that method cannot reach it.
     *
     * @param listenerClass AuthMe's {@code PaperDialogFlowListener} class
     * @param listener      the listener singleton holding the pending response maps
     * @param fieldName     {@code pendingRegisterResponses} or {@code pendingLoginResponses}
     * @param connection    the Paper connection object, or null
     * @param playerId      the player's connection UUID
     * @return the key the future was found under — the session id on 6.0.1, the player
     *         UUID on 6.0.0 — or null when no pending dialog was registered
     * @throws Exception when the response map cannot be read reflectively
     */
    static Object completePendingDialog(Class<?> listenerClass, Object listener, String fieldName,
            Object connection, UUID playerId) throws Exception {
        Field responsesField = listenerClass.getDeclaredField(fieldName);
        responsesField.setAccessible(true);
        Object responsesValue = responsesField.get(listener);
        if (!(responsesValue instanceof java.util.Map)) {
            return null;
        }
        java.util.Map<?, ?> responses = (java.util.Map<?, ?>) responsesValue;

        // AuthMe 6.0.1 — keyed by the session id opened for this connection
        Long sessionId = resolveDialogSessionId(listenerClass, listener, connection);
        if (sessionId != null && completeDialogFuture(responses.get(sessionId))) {
            return sessionId;
        }

        // AuthMe 6.0.0 — keyed by the player UUID assigned to the connection
        return completeDialogFuture(responses.get(playerId)) ? playerId : null;
    }

    /**
     * Resolves the preJoin dialog session id AuthMe opened for the given connection.
     *
     * @param listenerClass AuthMe's {@code PaperDialogFlowListener} class
     * @param listener      the listener singleton holding the session registry
     * @param connection    the Paper connection object, or null
     * @return the session id, or null when this AuthMe build keeps no session
     *         registry (6.0.0 and earlier) or has no open session for this connection
     */
    static Long resolveDialogSessionId(Class<?> listenerClass, Object listener, Object connection) {
        if (connection == null) {
            return null;
        }
        try {
            Field sessionsField = listenerClass.getDeclaredField("connectionSessions");
            sessionsField.setAccessible(true);
            Object sessions = sessionsField.get(listener);
            if (!(sessions instanceof java.util.Map)) {
                return null;
            }
            Object sessionId = ((java.util.Map<?, ?>) sessions).get(connection);
            return sessionId instanceof Long ? (Long) sessionId : null;
        } catch (NoSuchFieldException e) {
            // AuthMe 6.0.0 — the response maps are UUID-keyed, there is no session registry
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Completes a pending dialog future with null, which AuthMe reads as
     * "no kick message" and proceeds without kicking the player.
     *
     * @param future the value read from a response map, or null when absent
     * @return true when a future was present and has been completed
     */
    static boolean completeDialogFuture(Object future) {
        if (!(future instanceof java.util.concurrent.CompletableFuture)) {
            return false;
        }
        ((java.util.concurrent.CompletableFuture<?>) future).complete(null);
        return true;
    }
}
