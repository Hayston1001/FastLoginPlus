/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.shared.message;

import com.github.games647.fastlogin.shared.MessageColors;
import com.github.games647.fastlogin.shared.config.ConfigurationFiles;
import com.github.games647.fastlogin.shared.config.ConfigurationFiles.Loader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public final class LanguageMessages {

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
