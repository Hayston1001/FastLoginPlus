/*
 * SPDX-License-Identifier: MIT
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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.shared.message.LanguageMessages;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageMessagesTest {

    @TempDir
    Path folder;

    @Test
    void missingEnglishKeysAreEnumeratedWithoutReplacingSelectedMessages() throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        Configuration defaults = provider.load("missing: English fallback\nselected: English\n");
        Path file = folder.resolve("messages_selected.yml");
        Files.write(file, "selected: Selected language\n".getBytes(StandardCharsets.UTF_8));

        Configuration loaded = LanguageMessages.load(file, defaults, FastLoginCore.configurationLoader(provider));
        Map<String, String> messages = new HashMap<>();
        LanguageMessages.populate(loaded.getKeys(), loaded::get, messages);

        assertTrue(loaded.getKeys().contains("missing"));
        assertEquals("English fallback", messages.get("missing"));
        assertEquals("Selected language", messages.get("selected"));
    }

    @Test
    void absentLanguageFileUsesEnglishWithoutCreatingAFile() throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        Configuration defaults = provider.load("missing: English fallback\n");
        Path file = folder.resolve("absent.yml");

        Configuration loaded = LanguageMessages.load(file, defaults, FastLoginCore.configurationLoader(provider));
        Map<String, String> messages = new HashMap<>();
        LanguageMessages.populate(loaded.getKeys(), loaded::get, messages);

        assertEquals("English fallback", messages.get("missing"));
        assertFalse(Files.exists(file));
    }

    @Test
    void renderingKeepsColorsNewlinesAndExistingEntriesButSkipsEmptyAndNullMessages() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("colored", "&AHello/newline&LWorld &z &");
        raw.put("empty", "");
        Map<String, String> messages = new HashMap<>();
        messages.put("empty", "previous");

        LanguageMessages.populate(Arrays.asList("colored", "empty", "missing"), raw::get, messages);

        assertEquals("§aHello\n§lWorld &z &", messages.get("colored"));
        assertEquals("previous", messages.get("empty"));
        assertFalse(messages.containsKey("missing"));
    }
}
