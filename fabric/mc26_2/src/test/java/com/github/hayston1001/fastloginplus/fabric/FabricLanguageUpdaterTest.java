/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FabricLanguageUpdaterTest {

    @TempDir
    Path directory;

    @Test
    void appendsMissingKeysAndVersionWithoutMovingCommentsOrExistingKeys() throws Exception {
        Path file = directory.resolve("messages_en.yml");
        Files.writeString(file, """
                # Header comment
                custom: "Keep me"
                beta: "Old beta"
                """, StandardCharsets.UTF_8);
        Map<String, String> english = messages("_messages-version", "1",
                "beta", "New beta", "alpha", "New alpha");
        Map<String, String> localized = messages("_messages-version", "1",
                "beta", "Localized beta", "alpha", "Localized alpha");
        Logger logger = mock(Logger.class);

        FabricLanguageUpdater.Result result = FabricLanguageUpdater.update(file, english, localized, logger);

        assertEquals(1, result.added());
        assertTrue(result.versionUpdated());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("# Header comment", lines.get(0));
        assertEquals("custom: \"Keep me\"", lines.get(1));
        assertEquals("beta: \"Old beta\"", lines.get(2));
        assertTrue(lines.contains("_messages-version: 1"));
        assertTrue(lines.contains("alpha: \"Localized alpha\""));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("beta:")));
        verify(logger).warn(contains("not in the bundled defaults"), any(), any(), any());
    }

    @Test
    void currentVersionStillAddsMissingKeysButDoesNotRewriteTheVersion() throws Exception {
        Path file = directory.resolve("messages_en.yml");
        Files.writeString(file, "_messages-version: 1\nbeta: \"Old beta\"\n", StandardCharsets.UTF_8);
        Logger logger = mock(Logger.class);

        FabricLanguageUpdater.Result result = FabricLanguageUpdater.update(file,
                messages("_messages-version", "1", "beta", "New beta", "alpha", "New alpha"),
                messages("_messages-version", "1", "beta", "New beta", "alpha", "New alpha"), logger);

        assertTrue(result.added() > 0);
        assertFalse(result.versionUpdated());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("_messages-version: 1", lines.get(0));
        assertTrue(lines.contains("alpha: \"New alpha\""));
    }

    @Test
    void invalidMessageValuesAreLeftByteForByte() throws Exception {
        Path file = directory.resolve("messages_en.yml");
        String invalid = "premium-requested: [not, a, string]\n";
        Files.writeString(file, invalid, StandardCharsets.UTF_8);
        Logger logger = mock(Logger.class);

        FabricLanguageUpdater.update(file,
                messages("_messages-version", "1", "premium-requested", "valid"),
                messages("_messages-version", "1", "premium-requested", "valid"), logger);

        assertEquals(invalid, Files.readString(file, StandardCharsets.UTF_8));
        verify(logger).warn("Language file {} is not a valid message file; leaving it unchanged",
                Path.of("messages_en.yml"));
    }

    @Test
    void higherUserVersionIsNotDowngradedAndMissingKeysAreAppended() throws Exception {
        Path file = directory.resolve("messages_en.yml");
        Files.writeString(file, "_messages-version: 9\nbeta: \"Old beta\"\n", StandardCharsets.UTF_8);

        FabricLanguageUpdater.Result result = FabricLanguageUpdater.update(file,
                messages("_messages-version", "1", "beta", "New beta", "alpha", "New alpha"),
                messages("_messages-version", "1", "beta", "New beta", "alpha", "New alpha"),
                mock(Logger.class));

        assertTrue(result.added() > 0);
        assertFalse(result.versionUpdated());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("_messages-version: 9", lines.get(0));
    }

    @Test
    void versionUpgradeRefreshesOnlyUntouchedEnglishDefaultsAndKeepsCustomLines() throws Exception {
        Path file = directory.resolve("messages_zh.yml");
        Files.writeString(file, """
                _messages-version: 0
                # Keep this comment
                beta: "New beta"
                alpha: "My custom value"
                """, StandardCharsets.UTF_8);

        Logger logger = mock(Logger.class);
        FabricLanguageUpdater.update(file,
                messages("_messages-version", "1", "beta", "New beta", "alpha", "New alpha"),
                messages("_messages-version", "1", "beta", "本地 beta", "alpha", "本地 alpha"),
                logger);

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("_messages-version: 1", lines.get(0));
        assertEquals("# Keep this comment", lines.get(1));
        assertTrue(lines.contains("beta: \"本地 beta\""));
        assertTrue(lines.contains("alpha: \"My custom value\""));
    }

    private static Map<String, String> messages(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return values;
    }
}
