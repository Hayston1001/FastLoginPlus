/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.shared.config.ConfigurationFiles;
import com.github.hayston1001.fastloginplus.fabric.shared.config.ConfigRefresher;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Backend-local startup settings, refreshed from the bundled flat YAML template.
 */
final class FabricConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    private static final String FILE_NAME = "fastloginplus-fabric.yml";
    private static final String LEGACY_FILE_NAME = "fastloginplus-fabric.properties";

    private static StartupSettings startupSettings;
    private static RuntimeException startupFailure;

    private FabricConfig() {
    }

    /**
     * Loads the startup-only settings once, including an unavailable-file result.
     *
     * @return the immutable startup snapshot
     */
    static synchronized StartupSettings loadAtStartup() {
        if (startupSettings == null && startupFailure == null) {
            try {
                Map<String, Object> file = readFile(FabricLoader.getInstance().getConfigDir(), LOGGER);
                FabricMessages.load(stringValue(file, "language", "en"));
                startupSettings = new StartupSettings(stringValue(file, "allowed-proxy-ids", ""));
            } catch (RuntimeException unreadable) {
                startupFailure = unreadable;
            }
        }
        return startup();
    }

    static synchronized StartupSettings startup() {
        if (startupFailure != null) {
            throw startupFailure;
        }
        if (startupSettings == null) {
            throw new IllegalStateException("Fabric configuration was not loaded at startup");
        }
        return startupSettings;
    }

    static Map<String, Object> readFile(Path configDirectory, Logger logger) {
        Path file = configDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(configDirectory);
            List<String> template = templateLines();
            boolean created = !Files.exists(file);
            if (created) {
                Path legacy = configDirectory.resolve(LEGACY_FILE_NAME);
                Map<String, Object> initial = new LinkedHashMap<>();
                if (Files.exists(legacy)) {
                    Properties old = ConfigurationFiles.loadProperties(legacy);
                    try {
                        initial.put("language", FlatYamlReader.singleLine(old.getProperty("language", "en")));
                        initial.put("allowed-proxy-ids",
                                FlatYamlReader.singleLine(old.getProperty("allowed-proxy-ids", "")));
                    } catch (IllegalArgumentException unsupported) {
                        throw new IOException("Legacy settings cannot be represented as flat YAML strings",
                                unsupported);
                    }
                    ConfigRefresher.refresh(file, template, initial);
                    Path backup = legacy.resolveSibling(LEGACY_FILE_NAME + ".bak");
                    Files.move(legacy, backup);
                    logger.info("Migrated {} to {}; kept the original as {}", legacy, file, backup);
                } else {
                    ConfigRefresher.refresh(file, template, initial);
                    logger.info("Wrote default configuration to {}", file);
                }
            }
            FlatYamlReader.Result parsed = FlatYamlReader.read(Files.readAllLines(file, StandardCharsets.UTF_8));
            Map<String, Object> values = new LinkedHashMap<>(parsed.values());
            Object proxies = values.get("allowed-proxy-ids");
            if (proxies instanceof List<?> list) {
                values.put("allowed-proxy-ids", String.join(",", list.stream().map(String.class::cast).toList()));
            }
            if (parsed.unsupportedLine() != 0) {
                logger.warn("Unsupported YAML in {} at line {}; keeping earlier values and skipping template refresh",
                        file, parsed.unsupportedLine());
            } else if (!created) {
                ConfigRefresher.refresh(file, template, values);
            }
            return values;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load or refresh fastloginplus-fabric.yml", error);
        }
    }

    private static String stringValue(Map<String, Object> values, String key, String fallback) {
        return values.get(key) instanceof String value ? value : fallback;
    }

    static List<String> templateLines() throws IOException {
        try (InputStream input = FabricConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (input == null) {
                throw new IOException("Missing bundled fastloginplus-fabric.yml");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }

    /**
     * Warns about the one misconfiguration that silently breaks every upgrade:
     * an empty proxy trust list means the consent message is refused by the
     * proxy, so the queued upgrade can never complete.
     *
     * @param config the parsed settings
     */
    static void warnIfUntrusted(StartupSettings config) {
        warnIfUntrusted(config, LOGGER);
    }

    static void warnIfUntrusted(StartupSettings config, Logger logger) {
        if (config.allowedProxyIds().trim().isEmpty()) {
            logger.warn("fastloginplus-fabric.yml has an empty allowed-proxy-ids; the proxy "
                    + "will refuse the upgrade consent message and /flp premium can never complete. "
                    + "Fill in the proxy's UUID (its proxyId.txt).");
        }
    }

    /** Frozen at SERVER_STARTED; file edits require a backend restart. */
    record StartupSettings(String allowedProxyIds) {
    }
}
