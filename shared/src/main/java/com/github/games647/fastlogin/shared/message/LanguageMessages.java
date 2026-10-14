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
