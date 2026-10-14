/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
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
package com.github.games647.fastlogin.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;

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

    public static Properties loadProperties(Path file) throws IOException {
        Properties config = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            config.load(input);
        }
        return config;
    }

    private ConfigurationFiles() {
    }
}
