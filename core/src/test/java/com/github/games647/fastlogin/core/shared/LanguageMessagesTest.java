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
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class LanguageMessagesTest {

    @TempDir
    Path folder;

    @Test
    @SuppressWarnings("unchecked")
    void premiumWarningUsesTheMultilineDeliveryWithoutChangingTheStoredText() throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        for (String language : Arrays.asList("en", "zh")) {
            try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/messages_" + language + ".yml"), StandardCharsets.UTF_8)) {
                String warning = provider.load(reader).getString("premium-warning");
                assertTrue(warning.contains("\\n"));
                PlatformPlugin<Object> plugin = mock(PlatformPlugin.class, CALLS_REAL_METHODS);
                Object player = new Object();
                plugin.sendMultiLineMessage(player, warning);
                ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
                verify(plugin, times(2)).sendMessage(org.mockito.ArgumentMatchers.eq(player), lines.capture());
                assertFalse(lines.getAllValues().stream().anyMatch(line -> line.contains("\\n")));
            }
        }
    }

    @Test
    void defaultLanguageKeySetsStayInSync() throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        try (InputStreamReader english = new InputStreamReader(getClass().getResourceAsStream(
                "/messages_en.yml"), StandardCharsets.UTF_8);
             InputStreamReader chinese = new InputStreamReader(getClass().getResourceAsStream(
                     "/messages_zh.yml"), StandardCharsets.UTF_8)) {
            assertEquals(new java.util.HashSet<>(provider.load(english).getKeys()),
                    new java.util.HashSet<>(provider.load(chinese).getKeys()));
        }
    }

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
