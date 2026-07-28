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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import fr.xephi.authme.api.v3.AuthMeApi;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integrates FastLoginPlus with AuthMe 6.0's premium system via reflection.
 *
 * <p>Provides three injection points:
 * <ul>
 *   <li>{@link #forceRegisterInAuthMe} — auto-register a premium player in AuthMe's DB</li>
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

    private final FastLoginBukkit plugin;
    private final AuthMeVersionDetector versionDetector;

    // Cached reflection handles (lazy-initialized)
    private Object authMeInjector;
    private Object pendingPremiumCache;
    private Object premiumLoginVerifier;
    private Object dataSource;
    private Object playerCache;

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
            plugin.getLog().debug("Could not read AuthMe enablePremium: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns true if AuthMe 6.0's preJoin dialog UI is enabled.
     * Reads from AuthMe's config.yml; defaults to true (AuthMe's default).
     *
     * @return true if preJoin dialog is enabled
     */
    public boolean isPreJoinDialogEnabled() {
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
            return config.getBoolean("settings.registration.dialog.preJoin.enable", true);
        } catch (Exception e) {
            plugin.getLog().debug("Could not read AuthMe preJoin setting: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Auto-register a premium player in AuthMe's database.
     * Calls AuthMeApi.forceRegister(player, generatedPassword) which also auto-logs in.
     * No-op if AuthMe 6.0 is not present.
     *
     * @param player the player to register
     * @return true if registration succeeded
     */
    public boolean forceRegisterInAuthMe(Player player) {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        try {
            AuthMeApi api = AuthMeApi.getInstance();
            if (api == null) {
                return false;
            }
            if (api.isRegistered(player.getName())) {
                return false;
            }

            String generatedPassword = plugin.getCore()
                .getPasswordGenerator().getRandomPassword(player);
            api.forceRegister(player, generatedPassword);
            plugin.getLog().info("Auto-registered {} in AuthMe (premium)", player.getName());
            return true;
        } catch (Exception e) {
            plugin.getLog().debug("AuthMe forceRegister failed: {}", e.getMessage());
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
            plugin.getLog().debug("Injected pending premium for {} into AuthMe", playerName);
        } catch (Exception e) {
            plugin.getLog().debug("PendingPremiumCache injection failed: {}", e.getMessage());
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
            plugin.getLog().debug("Injected verified UUID for {} into AuthMe", playerName);
        } catch (Exception e) {
            plugin.getLog().debug("PremiumLoginVerifier injection failed: {}", e.getMessage());
        }
    }

    /**
     * Directly set the premium UUID in AuthMe's database for the given player.
     * If the player has no AuthMe DB record (first login), pre-creates one
     * with the premium UUID already set so that the preJoin dialog is skipped.
     *
     * @param playerName the player name
     * @param mojangUuid the verified Mojang UUID
     * @return true if a new DB record was pre-created, false if an existing
     *         record was updated or the operation failed
     */
    public boolean markPlayerAsPremium(String playerName, UUID mojangUuid) {
        if (!versionDetector.isAuthMe6()) {
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
                preCreatePremiumAuth(dataSource, lowerName, playerName, mojangUuid);
                return true;
            }

            // Set premium UUID on existing record
            Method setPremiumUuid = auth.getClass().getMethod("setPremiumUuid", UUID.class);
            setPremiumUuid.invoke(auth, mojangUuid);

            // Update in database
            Method updatePremium = dataSource.getClass().getMethod(
                "updatePremiumUuid", auth.getClass());
            boolean success = (boolean) updatePremium.invoke(dataSource, auth);

            if (success) {
                plugin.getLog().info("Marked {} as premium in AuthMe database", playerName);
            }
            return false;
        } catch (Exception e) {
            plugin.getLog().debug("markPlayerAsPremium failed: {}", e.getMessage());
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
        if (!versionDetector.isAuthMePresent()) {
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
                    plugin.getLog().debug("Removed {} from AuthMe PendingPremiumCache", playerName);
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to clear AuthMe PendingPremiumCache for {}: {}",
                    playerName, e.getMessage());
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
                    plugin.getLog().debug("Removed {} from AuthMe PremiumLoginVerifier", playerName);
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to clear AuthMe PremiumLoginVerifier for {}: {}",
                    playerName, e.getMessage());
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
                    plugin.getLog().info("AuthMe removeAuth({}) = {} (switched to cracked)", playerName, removed);

                    if (!removed) {
                        // Tier 2: record still exists — clear premium_uuid + reset password
                        Method getAuth = ds.getClass().getMethod("getAuth", String.class);
                        Object auth = getAuth.invoke(ds, lowerName);
                        if (auth != null) {
                            fallbackClearAuthMeRecord(ds, auth, playerName);
                        }
                    }
                }

                // b) Remove from AuthMe's in-memory player cache (synchronous)
                Object pc = getPlayerCache();
                if (pc == null) {
                    plugin.getLog().debug("Cannot clear AuthMe PlayerCache for {}: not available", playerName);
                } else {
                    Method removePlayer = pc.getClass().getMethod("removePlayer", String.class);
                    removePlayer.invoke(pc, lowerName);
                    plugin.getLog().debug("Removed {} from AuthMe PlayerCache", playerName);
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to unregister {} from AuthMe 6.0: {}",
                    playerName, e.getMessage());
                plugin.getLog().debug("AuthMe unregister exception trace", e);
            }
        } else {
            // AuthMe 5.x: no premium feature, no caches.
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
                        plugin.getLog().info(
                            "AuthMe 5.x removeAuth({}) = {} (synchronous)", playerName, removed);
                        if (removed) {
                            return; // done synchronously
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLog().debug(
                    "AuthMe 5.x synchronous cleanup failed for {}, falling back to async API: {}",
                    playerName, e.getMessage());
            }

            // Fallback: async AuthMeApi
            try {
                AuthMeApi api = AuthMeApi.getInstance();
                if (api != null && api.isRegistered(lowerName)) {
                    api.forceUnregister(lowerName);
                    plugin.getLog().info("Unregistered {} from AuthMe 5.x (async fallback)", playerName);
                }
            } catch (Exception e) {
                plugin.getLog().warn("Failed to unregister {} from AuthMe 5.x: {}",
                    playerName, e.getMessage());
            }
        }
    }

    /**
     * Lightweight check called during cracked-session login to detect and clean
     * up stale AuthMe premium records left over from a failed
     * {@link #clearPlayerPremium(String)}. Only triggers full cleanup if the
     * AuthMe record still has {@code isPremium()=true} — normal cracked players
     * are not affected.
     *
     * @param playerName the player name
     */
    public void ensureNotPremium(String playerName) {
        if (!versionDetector.isAuthMe6()) {
            return;
        }

        String lowerName = playerName.toLowerCase(java.util.Locale.ROOT);
        try {
            Object ds = getDataSource();
            if (ds == null) {
                return;
            }

            Method getAuth = ds.getClass().getMethod("getAuth", String.class);
            Object auth = getAuth.invoke(ds, lowerName);
            if (auth == null) {
                return; // no record — nothing to clean
            }

            Method isPremium = auth.getClass().getMethod("isPremium");
            boolean premium = (boolean) isPremium.invoke(auth);
            if (!premium) {
                return; // normal cracked player — don't touch
            }

            // Stale premium record from a failed /cracked cleanup
            plugin.getLog().info(
                "[FLP] Stale premium record for {} detected during cracked login, cleaning up",
                playerName);
            clearPlayerPremium(playerName);
        } catch (Exception e) {
            plugin.getLog().debug(
                "ensureNotPremium check failed for {}: {}", playerName, e.getMessage());
        }
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
        plugin.getLog().info("Cleared premium flag for {} in AuthMe (fallback)", playerName);

        // Reset password to empty → player can re-register
        Class<?> hashedPwClass = Class.forName("fr.xephi.authme.security.crypts.HashedPassword");
        Object emptyHash = hashedPwClass.getConstructor(String.class).newInstance("");
        Method setPassword = auth.getClass().getMethod("setPassword", hashedPwClass);
        setPassword.invoke(auth, emptyHash);
        Method saveAuth = ds.getClass().getMethod("saveAuth", auth.getClass());
        saveAuth.invoke(ds, auth);
        plugin.getLog().info("Reset password for {} in AuthMe (fallback)", playerName);
    }

    /**
     * Pre-creates a PlayerAuth record with the premium UUID already set,
     * so that AuthMe's preJoin dialog check sees isPremium()=true and
     * skips the blocking register dialog for first-time premium players.
     *
     * <p>Uses reflection to call DataSource.saveAuth(PlayerAuth). The
     * PlayerAuth is built with a random password (the player never needs
     * it — premium bypass skips password auth entirely) and the Mojang
     * UUID as both the premium UUID and the player UUID.
     *
     * @param dataSource AuthMe's DataSource singleton
     * @param lowerName lowercase player name (DB key)
     * @param playerName original-case player name (for realName field)
     * @param mojangUuid the verified Mojang UUID
     */
    private void preCreatePremiumAuth(Object dataSource, String lowerName,
                                       String playerName, UUID mojangUuid) throws Exception {
        // Build: PlayerAuth.builder().name(lowerName).realName(playerName)
        //   .password(new HashedPassword(randomHash)).uuid(mojangUuid).premiumUuid(mojangUuid).build()
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

        // saveAuth does NOT insert the premium_uuid column (AuthMe's AbstractSqlDataSource
        // only inserts NAME, NICK_NAME, PASSWORD, SALT, EMAIL, REGISTRATION_DATE,
        // REGISTRATION_IP, UUID). We must call updatePremiumUuid separately to persist it.
        if (success) {
            Method setPremiumUuid = playerAuth.getClass().getMethod("setPremiumUuid", UUID.class);
            setPremiumUuid.invoke(playerAuth, mojangUuid);
            Method updatePremium = dataSource.getClass().getMethod(
                "updatePremiumUuid", playerAuth.getClass());
            updatePremium.invoke(dataSource, playerAuth);
            plugin.getLog().info(
                "Pre-created premium AuthMe record for {} (uuid={})", playerName, mojangUuid);
        } else {
            plugin.getLog().warn(
                "Failed to pre-create premium AuthMe record for {}", playerName);
        }
    }

    // --- Reflection helpers ---

    /**
     * Returns AuthMe's {@code ch.jalu.injector.Injector} instance by reflecting on the
     * {@code AuthMe.injector} field. Cached after first successful lookup.
     *
     * @return the injector instance, or null if not found
     */
    private Object getAuthMeInjector() throws Exception {
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
        plugin.getLog().debug("Could not find AuthMe's Injector field");
        return null;
    }

    /**
     * Gets the {@code PendingPremiumCache} singleton from AuthMe's DI injector.
     * The injector's {@code getSingleton()} method returns the managed instance.
     *
     * @return the PendingPremiumCache instance, or null if not found
     */
    private Object getPendingPremiumCache() throws Exception {
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
    private Object getPremiumLoginVerifier() throws Exception {
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
    private Object getDataSource() throws Exception {
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
     *   <li>Call PacketEventsService.reload(settings) to re-evaluate listener
     *       registration with the new setting</li>
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

            // 4. Reload PacketEventsService so it re-evaluates with enablePremium=true
            reloadPacketEventsService(injector, settings);

            return true;
        } catch (Exception e) {
            plugin.getLog().error("Failed to force-enable AuthMe enablePremium: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Unregisters AuthMe's {@code PremiumVerificationPacketListener} so FLP's
     * ProtocolLib listener is the sole packet-level Mojang verification.
     *
     * <p>When {@code enablePremium=true}, AuthMe registers its own PacketEvents
     * listener that intercepts START/ENCRYPTION_RESPONSE packets. This conflicts
     * with FLP's ProtocolLib listener which does the same thing. FLP does the
     * actual verification and injects results into AuthMe's internal state
     * (PendingPremiumCache, PremiumLoginVerifier), so AuthMe's own listener is
     * redundant and must be removed.
     *
     * <p>Uses AuthMe's public {@code PacketInterceptionAdapter.unregisterPremiumVerification()}
     * method — the same method AuthMe itself uses to clean up the listener.
     *
     * @return true if the listener was unregistered (or was already unregistered)
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

            // Get PacketInterceptionAdapter (implemented by PacketEventsListenerRegistry)
            Method getSingleton = injector.getClass().getMethod("getSingleton", Class.class);
            Class<?> adapterClass = Class.forName(
                "fr.xephi.authme.platform.PacketInterceptionAdapter");
            Object adapter = getSingleton.invoke(injector, adapterClass);
            if (adapter == null) {
                return false;
            }

            // Check if the premium verification listener is registered
            // by reflecting on PacketEventsService.premiumVerificationRegistered
            Class<?> pesClass = Class.forName(
                "fr.xephi.authme.listener.packetevents.PacketEventsService");
            Object pes = getSingleton.invoke(injector, pesClass);
            if (pes != null) {
                Field registeredField = pesClass.getDeclaredField("premiumVerificationRegistered");
                registeredField.setAccessible(true);
                boolean isRegistered = registeredField.getBoolean(pes);
                if (!isRegistered) {
                    // Already unregistered — nothing to do
                    return true;
                }
            }

            // Call unregisterPremiumVerification() on the adapter
            Method unregister = adapterClass.getMethod("unregisterPremiumVerification");
            unregister.invoke(adapter);

            // Set premiumVerificationRegistered = false to keep AuthMe's state consistent
            if (pes != null) {
                Field registeredField = pesClass.getDeclaredField("premiumVerificationRegistered");
                registeredField.setAccessible(true);
                registeredField.setBoolean(pes, false);
            }

            plugin.getLog().info(
                "Unregistered AuthMe's PremiumVerificationPacketListener — "
                + "FLP is now the sole Mojang verification source.");
            return true;
        } catch (Exception e) {
            plugin.getLog().debug("Failed to unregister premium packet listener: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Performs the full AuthMe 6.0 takeover: forces enablePremium=true and
     * unregisters AuthMe's redundant packet listener. Called at startup after
     * AuthMe has fully initialized, and can be called again to re-assert
     * (e.g. after /authme reload).
     *
     * @return true if both operations succeeded
     */
    public boolean enforceFlpPremiumControl() {
        if (!versionDetector.isAuthMe6()) {
            return false;
        }
        boolean forced = forceEnablePremium();
        boolean unregistered = unregisterPremiumPacketListener();
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
}
