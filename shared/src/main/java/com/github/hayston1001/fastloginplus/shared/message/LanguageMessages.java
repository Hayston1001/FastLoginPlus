/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.shared.message;

import com.github.hayston1001.fastloginplus.shared.MessageColors;
import com.github.hayston1001.fastloginplus.shared.config.ConfigurationFiles;
import com.github.hayston1001.fastloginplus.shared.config.ConfigurationFiles.Loader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public final class LanguageMessages {

    /** A loaded value and whether it came from the caller's bundled fallback. */
    public static final class Result<T> {
        private final T value;
        private final boolean fallback;

        private Result(T value, boolean fallback) {
            this.value = value;
            this.fallback = fallback;
        }

        public T getValue() {
            return value;
        }

        public boolean isFallback() {
            return fallback;
        }
    }

    /**
     * Reads an override without writing it, using a caller-prepared fallback on read or parser failure.
     * The callback runs once for that failure. Exceptions from the callback propagate to the caller;
     * they are not treated as another load failure. JVM errors are not caught.
     *
     * @param <T> caller-owned parsed message type
     * @param userFile override to read, if present
     * @param defaults defaults used for missing override keys or an absent file
     * @param bundled complete fallback value, prepared by the caller before loading
     * @param loader caller-supplied parser and key access
     * @param onFailure receives the original read or parser failure
     * @return the loaded value, or the bundled value marked as a fallback
     */
    public static <T> Result<T> loadWithFallback(Path userFile, T defaults, T bundled,
                                                Loader<T> loader, Consumer<Throwable> onFailure) {
        Objects.requireNonNull(userFile, "userFile");
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(bundled, "bundled");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(onFailure, "onFailure");
        try {
            return new Result<>(load(userFile, defaults, loader), false);
        } catch (IOException | RuntimeException failure) {
            onFailure.accept(failure);
            return new Result<>(bundled, true);
        }
    }

    public static <T> T load(Path filePath, T defaults, Loader<T> loader) throws IOException {
        T config;
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                config = loader.load(reader, defaults);
            }
        } else {
            // Language file does not exist, use defaults only.
            config = defaults;
        }

        return ConfigurationFiles.includeDefaultKeys(config, defaults, loader);
    }

    public static void populate(Collection<String> keys, Function<String, Object> messages,
                                Map<String, String> localeMessages) {
        keys.stream()
                .filter(key -> !key.startsWith("_"))
                .filter(key -> messages.apply(key) != null)
                .collect(toMap(identity(), messages))
                .forEach((key, message) -> {
                    String colored = MessageColors.translateColorCodes((String) message);
                    if (!colored.isEmpty()) {
                        localeMessages.put(key, colored.replace("/newline", "\n"));
                    }
                });
    }

    private LanguageMessages() {
    }
}
