/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.shared.config.ConfigurationFiles;
import com.github.hayston1001.fastloginplus.fabric.shared.message.LanguageMessages;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;

/** Backend-local UTF-8 messages with per-key English fallback. */
final class FabricMessages {
    private static final Properties ENGLISH = bundled("en");
    private static volatile Properties messages = ENGLISH;

    private FabricMessages() {
    }

    static void load(String language) {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("fastloginplus-fabric");
        messages = load(language, directory);
    }

    static Properties load(String language, Path directory) {
        String locale = "zh".equalsIgnoreCase(language) ? "zh" : "en";
        Path file = directory.resolve("messages_" + locale + ".properties");
        Properties defaults = bundled("en");
        // An absent override uses the bundled locale. A selected override file
        // with a missing key falls back to English, just like the classic loader.
        if (!Files.exists(file) && "zh".equals(locale)) {
            defaults.putAll(bundled(locale));
        }
        try {
            return LanguageMessages.load(file,
                    defaults, new ConfigurationFiles.Loader<Properties>() {
                        @Override
                        public Properties load(Reader reader, Properties fallback) throws IOException {
                            Properties result = new Properties(fallback);
                            result.load(reader);
                            return result;
                        }

                        @Override
                        public Collection<String> getKeys(Properties config) {
                            return config.stringPropertyNames();
                        }

                        @Override
                        public Object get(Properties config, String key) {
                            String value = config.getProperty(key);
                            return value == null || value.isBlank() ? defaults.getProperty(key) : value;
                        }

                        @Override
                        public void set(Properties config, String key, Object value) {
                            config.put(key, value);
                        }
                    });
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load Fabric language messages", error);
        }
    }

    private static Properties bundled(String language) {
        Properties values = new Properties();
        try (InputStream input = FabricMessages.class.getResourceAsStream("/messages_" + language + ".properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled Fabric language: " + language);
            }
            values.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return values;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read bundled Fabric language", error);
        }
    }

    static Component text(String key, String... replacements) {
        String value = messages.getProperty(key, ENGLISH.getProperty(key, key));
        for (int i = 0; i < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return Component.literal(value);
    }

    static PlayerFailure failure(String key) {
        return new PlayerFailure(key);
    }

    static PlayerFailure failure(String key, Throwable cause) {
        PlayerFailure failure = new PlayerFailure(key);
        failure.initCause(cause);
        return failure;
    }

    static Component reason(Throwable error, String fallback) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof PlayerFailure failure) {
                return text(failure.key);
            }
        }
        return text(fallback);
    }

    static final class PlayerFailure extends IllegalStateException {
        private final String key;

        private PlayerFailure(String key) {
            super(key);
            this.key = key;
        }
    }
}
