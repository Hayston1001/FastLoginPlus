/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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

    private FabricConfig() {
    }

    /**
     * Writes the commented default on first start and loads the settings.
     *
     * @return the parsed settings
     */
    static Properties load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        writeDefaultOnce(file);
        try {
            return ConfigurationFiles.loadProperties(file);
        } catch (IOException error) {
            throw new IllegalStateException("Configure binding-transfer-host and binding-transfer-port", error);
        }
    }

    /**
     * Warns about the one misconfiguration that silently breaks every upgrade:
     * an empty proxy trust list means the consent message is refused by the
     * proxy, so the queued upgrade can never complete.
     *
     * @param config the parsed settings
     */
    static void warnIfUntrusted(Properties config) {
        if (config.getProperty("allowed-proxy-ids", "").trim().isEmpty()) {
            LOGGER.warn("fastloginplus-fabric.properties has an empty allowed-proxy-ids; the proxy "
                    + "will refuse the upgrade consent message and /flp premium can never complete. "
                    + "Fill in the proxy's UUID (its proxyId.txt).");
        }
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
