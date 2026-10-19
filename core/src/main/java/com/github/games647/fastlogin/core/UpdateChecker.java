/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.github.hayston1001.fastloginplus.shared.update.VersionComparator;
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
    private final boolean debug;
    private volatile String latestVersion;

    public UpdateChecker(Logger logger, String currentVersion, boolean debug) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.debug = debug;
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
            if (tag != null && VersionComparator.isNewer(tag, currentVersion)) {
                latestVersion = tag;
                return true;
            }
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.warn("Update check failed, network unreachable: {}", reason);
            if (debug) {
                logger.info("Update check failed (full trace)", e);
            }
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

}
