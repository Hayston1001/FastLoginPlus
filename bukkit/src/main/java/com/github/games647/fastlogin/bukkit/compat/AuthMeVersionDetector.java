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
