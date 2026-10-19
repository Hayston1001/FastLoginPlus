/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.compat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Detects AuthMe version and feature availability at runtime.
 * Uses class existence checks to avoid compile-time dependency on AuthMe 6.0 classes.
 */
public final class AuthMeVersionDetector {

    private final boolean authMePresent;
    private final boolean authMe6;
    private final String version;

    public AuthMeVersionDetector() {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        this.authMePresent = authMe != null;
        this.version = authMePresent ? authMe.getDescription().getVersion() : "";

        // PendingPremiumCache exists only in AuthMe 6.0+
        // This is more reliable than parsing version strings
        this.authMe6 = authMePresent && isClassAvailable(
            "fr.xephi.authme.service.PendingPremiumCache");
    }

    public boolean isAuthMePresent() {
        return authMePresent;
    }

    /**
     * Returns true if AuthMe 6.0+ is installed.
     * Detection is based on class existence (PendingPremiumCache),
     * not version string parsing.
     *
     * @return true if AuthMe 6.0+ detected
     */
    public boolean isAuthMe6() {
        return authMe6;
    }

    /**
     * Returns the raw version string from plugin.yml, or empty if AuthMe is not installed.
     *
     * @return version string
     */
    public String getVersion() {
        return version;
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
