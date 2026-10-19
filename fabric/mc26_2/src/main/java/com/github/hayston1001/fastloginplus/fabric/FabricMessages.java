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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Backend-local UTF-8 messages with per-key English fallback. */
final class FabricMessages {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fastloginplus_fabric");
    private static final Map<String, String> ENGLISH = bundled("en");
    private static volatile Map<String, String> messages = ENGLISH;

    private FabricMessages() {
    }

    static void load(String language) {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("fastloginplus-fabric");
        messages = loadAndReportLanguage(language, directory, LOGGER);
    }

    static Map<String, String> loadAndReportLanguage(String language, Path directory, org.slf4j.Logger logger) {
        Map<String, String> loaded = load(language, directory);
        if (language != null && !language.isEmpty()
                && !"en".equalsIgnoreCase(language) && !"zh".equalsIgnoreCase(language)) {
            logger.warn("Unknown Fabric message language='{}'; effective language={}",
                    language, effectiveLanguage(language));
        }
        logger.info("Fabric message language={} (set language in fastloginplus-fabric.yml; "
                + "restart the backend to apply changes)", effectiveLanguage(language));
        return loaded;
    }

    private static String effectiveLanguage(String language) {
        return "zh".equalsIgnoreCase(language) ? "zh" : "en";
    }

    static Map<String, String> load(String language, Path directory) {
        String locale = effectiveLanguage(language);
        Path file = directory.resolve("messages_" + locale + ".yml");
        Map<String, String> defaults = new LinkedHashMap<>(bundled("en"));
        // An absent override uses the bundled locale. A selected override file
        // with a missing key falls back to English, just like the classic loader.
        if (!Files.exists(file) && "zh".equals(locale)) {
            defaults.putAll(bundled(locale));
        }
        try {
            return LanguageMessages.load(file, defaults, YAML_LOADER);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load Fabric language messages", error);
        }
    }

    private static Map<String, String> loadYaml(Reader reader, Map<String, String> defaults) throws IOException {
        List<String> lines;
        try (BufferedReader buffered = new BufferedReader(reader)) {
            lines = buffered.lines().toList();
        }
        FlatYamlReader.Result parsed = FlatYamlReader.readMessages(lines);
        if (parsed.unsupportedLine() != 0) {
            throw new IOException("Unsupported YAML in Fabric messages at line " + parsed.unsupportedLine());
        }
        Map<String, String> values = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, Object> entry : parsed.values().entrySet()) {
            if (!(entry.getValue() instanceof String text)) {
                throw new IOException("Fabric message '" + entry.getKey() + "' must be a string");
            }
            values.put(entry.getKey(), text);
        }
        defaults.forEach((key, fallback) -> {
            String current = values.get(key);
            if (current == null || current.isBlank()) {
                values.put(key, fallback);
            }
        });
        return values;
    }

    private static final ConfigurationFiles.Loader<Map<String, String>> YAML_LOADER =
            new ConfigurationFiles.Loader<>() {
                @Override
                public Map<String, String> load(Reader reader, Map<String, String> defaults) throws IOException {
                    return loadYaml(reader, defaults);
                }

                @Override
                public Collection<String> getKeys(Map<String, String> config) {
                    return config.keySet();
                }

                @Override
                public Object get(Map<String, String> config, String key) {
                    return config.get(key);
                }

                @Override
                public void set(Map<String, String> config, String key, Object value) {
                    config.put(key, String.valueOf(value));
                }
            };

    private static Map<String, String> bundled(String language) {
        try (InputStream input = FabricMessages.class.getResourceAsStream("/messages_" + language + ".yml")) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled Fabric language: " + language);
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return loadYaml(reader, Map.of());
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read bundled Fabric language", error);
        }
    }

    static Component text(String key, String... replacements) {
        return Component.literal(plain(key, replacements));
    }

    static String plain(String key, String... replacements) {
        String value = messages.getOrDefault(key, ENGLISH.getOrDefault(key, key));
        for (int i = 0; i < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return value;
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
