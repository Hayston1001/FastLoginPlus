/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.shared.update.VersionComparator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Checks GitHub Releases for a newer FastLoginPlus version.
 */
final class FabricUpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/Hayston1001/FastLoginPlus/releases/latest";

    private final Logger logger;
    private final String currentVersion;
    private final boolean debug;
    private volatile String latestVersion;

    FabricUpdateChecker(Logger logger, String currentVersion, boolean debug) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.debug = debug;
    }

    String getCurrentVersion() {
        return currentVersion;
    }

    String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Performs one GitHub Releases check.
     *
     * @return true if a newer version is available
     */
    boolean checkForUpdates() {
        try {
            String tag = fetchLatestTag();
            if (tag != null && VersionComparator.isNewer(tag, currentVersion)) {
                latestVersion = tag;
                return true;
            }
        } catch (Exception error) {
            String reason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            logger.warn("Update check failed, network unreachable: {}", reason);
            if (debug) {
                logger.info("Update check failed (full trace)", error);
            }
        }
        return false;
    }

    private String fetchLatestTag() throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "FastLoginPlus");
        try {
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return json.get("tag_name").getAsString();
            }
        } finally {
            connection.disconnect();
        }
    }
}
