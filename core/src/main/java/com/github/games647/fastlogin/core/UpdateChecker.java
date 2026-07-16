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
package com.github.games647.fastlogin.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;

/**
 * Checks GitHub Releases for new versions of FastLoginPlus.
 */
public class UpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/Hayston1001/FastLoginPlus/releases/latest";

    private final Logger logger;
    private final String currentVersion;
    private volatile String latestVersion;

    public UpdateChecker(Logger logger, String currentVersion) {
        this.logger = logger;
        this.currentVersion = currentVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    /**
     * @return the latest remote version tag, or null if no update available or check failed
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Performs an HTTP request to GitHub to check for updates.
     *
     * @return true if a newer version is available
     */
    public boolean checkForUpdates() {
        try {
            String tag = fetchLatestTag();
            if (tag != null && isNewer(tag, currentVersion)) {
                latestVersion = tag;
                return true;
            }
        } catch (Exception e) {
            logger.debug("Update check failed", e);
        }
        return false;
    }

    private String fetchLatestTag() throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "FastLoginPlus");

        try {
            if (conn.getResponseCode() != 200) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return json.get("tag_name").getAsString();
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Compares two version strings numerically (e.g. "0.1.0" vs "0.0.7").
     * Handles optional "v" prefix and pre-release suffix (e.g. "v1.0.0-beta1").
     *
     * @param remote the remote version tag
     * @param local the current local version
     * @return true if remote is newer than local
     */
    private boolean isNewer(String remote, String local) {
        int[] remoteParts = parseVersion(remote);
        int[] localParts = parseVersion(local);
        int maxLen = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < maxLen; i++) {
            int r = i < remoteParts.length ? remoteParts[i] : 0;
            int l = i < localParts.length ? localParts[i] : 0;
            if (r > l) {
                return true;
            }
            if (r < l) {
                return false;
            }
        }
        return false;
    }

    private int[] parseVersion(String version) {
        String cleaned = version.startsWith("v") ? version.substring(1) : version;
        // strip pre-release suffix (e.g. "1.0.0-beta1" -> "1.0.0")
        String numeric = cleaned.split("-")[0];
        String[] parts = numeric.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
}
