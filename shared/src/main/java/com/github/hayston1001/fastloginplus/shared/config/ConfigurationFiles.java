/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.shared.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public final class ConfigurationFiles {

    /** Parser and default-value access supplied by the caller. */
    public interface Loader<T> {

        T load(Reader reader, T defaults) throws IOException;

        Collection<String> getKeys(T config);

        Object get(T config, String key);

        void set(T config, String key, Object value);
    }

    public static <T> T load(Path file, T defaults, Loader<T> loader) throws IOException {
        T config;
        try (Reader reader = Files.newBufferedReader(file)) {
            config = loader.load(reader, defaults);
        }

        return includeDefaultKeys(config, defaults, loader);
    }

    public static <T> T includeDefaultKeys(T config, T defaults, Loader<T> loader) {
        // Some parsers do not enumerate keys inherited from defaults.
        for (String key : loader.getKeys(defaults)) {
            loader.set(config, key, loader.get(config, key));
        }

        return config;
    }

    private ConfigurationFiles() {
    }
}
