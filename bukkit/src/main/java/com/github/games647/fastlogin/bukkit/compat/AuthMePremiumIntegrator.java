/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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
}
