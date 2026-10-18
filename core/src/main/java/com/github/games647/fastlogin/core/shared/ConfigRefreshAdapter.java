/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.md_5.bungee.config.Configuration;

/** Core-owned template access and configuration conversion for the shared refresher. */
final class ConfigRefreshAdapter {

    static List<String> readTemplateLines(ClassLoader classLoader, String resource) throws IOException {
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                return null;
            }
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Flatten the defaults-merged configuration returned by the core loader.
     *
     * @param config the configuration including defaults
     * @return ordered dotted keys and non-null leaf values
     */
    static Map<String, Object> flattenConfig(Configuration config) {
        Map<String, Object> result = new LinkedHashMap<>();
        flattenConfig(config, "", result);
        return result;
    }

    private static void flattenConfig(Configuration config, String prefix, Map<String, Object> result) {
        for (String key : config.getKeys()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = config.get(key);
            if (value instanceof Configuration) {
                flattenConfig((Configuration) value, fullKey, result);
            } else if (value != null) {
                result.put(fullKey, value);
            }
        }
    }

    private ConfigRefreshAdapter() {
    }
}
