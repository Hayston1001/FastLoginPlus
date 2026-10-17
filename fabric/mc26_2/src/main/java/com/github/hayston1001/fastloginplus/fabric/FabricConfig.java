/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.shared.config.ConfigurationFiles;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The backend-local settings file. The commented default ships with the mod and
 * is written out on first start, so every key is documented where the admin
 * edits it (D-07).
 */
final class FabricConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    private static final String FILE_NAME = "fastloginplus-fabric.properties";

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
                Properties file = readFile();
                FabricMessages.load(file.getProperty("language", "en"));
                startupSettings = new StartupSettings(file.getProperty("allowed-proxy-ids", ""));
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

    private static Properties readFile() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        writeDefaultOnce(file);
        try {
            return ConfigurationFiles.loadProperties(file);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read fastloginplus-fabric.properties", error);
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
        if (config.allowedProxyIds().trim().isEmpty()) {
            LOGGER.warn("fastloginplus-fabric.properties has an empty allowed-proxy-ids; the proxy "
                    + "will refuse the upgrade consent message and /flp premium can never complete. "
                    + "Fill in the proxy's UUID (its proxyId.txt).");
        }
    }

    /** Frozen at SERVER_STARTED; file edits require a backend restart. */
    record StartupSettings(String allowedProxyIds) {
    }

    private static void writeDefaultOnce(Path file) {
        if (Files.exists(file)) {
            return;
        }
        try (InputStream template = FabricConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (template == null) {
                LOGGER.warn("No default {} shipped with this build; create it by hand", file);
                return;
            }
            Files.copy(template, file);
            LOGGER.info("Wrote default configuration to {}", file);
        } catch (IOException error) {
            LOGGER.warn("Could not write the default {}: {}", file, error.getMessage());
        }
    }
}
