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
 *       shouldSkipPreJoinDialogForPremium() returns true</li>
 * </ul>
 *
 * <p>All methods are no-ops if AuthMe 6.0 is not detected.
 */
public final class AuthMePremiumIntegrator {

    private final FastLoginBukkit plugin;
    private final AuthMeVersionDetector versionDetector;

    // Cached reflection handles (lazy-initialized)
    private Object authMeApiInstance;
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
     * This causes shouldSkipPreJoinDialogForPremium() to return true (skips dialog).
     * No-op if AuthMe 6.0 is not present.
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
     * This enables canBypassWithPremium() to find a verified session (60s TTL).
     * No-op if AuthMe 6.0 is not present.
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

    // --- Reflection helpers ---

    private Object getPendingPremiumCache() throws Exception {
        if (pendingPremiumCache != null) {
            return pendingPremiumCache;
        }
        authMeApiInstance = AuthMeApi.getInstance();
        if (authMeApiInstance == null) {
            return null;
        }
        pendingPremiumCache = findFieldByType(authMeApiInstance.getClass(),
            "fr.xephi.authme.service.PendingPremiumCache", authMeApiInstance);
        return pendingPremiumCache;
    }

    private Object getPremiumLoginVerifier() throws Exception {
        if (premiumLoginVerifier != null) {
            return premiumLoginVerifier;
        }
        if (authMeApiInstance == null) {
            authMeApiInstance = AuthMeApi.getInstance();
        }
        if (authMeApiInstance == null) {
            return null;
        }
        premiumLoginVerifier = findFieldByType(authMeApiInstance.getClass(),
            "fr.xephi.authme.service.PremiumLoginVerifier", authMeApiInstance);
        return premiumLoginVerifier;
    }

    private static Object findFieldByType(Class<?> clazz, String typeName, Object instance)
            throws Exception {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getName().equals(typeName)) {
                    f.setAccessible(true);
                    return f.get(instance);
                }
            }
        }
        return null;
    }
}
