/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FabricMessagesTest {
    @TempDir
    Path directory;

    @Test
    void bundledLanguagesHaveIdenticalKeysAndChineseIsUtf8() throws Exception {
        Properties english = bundled("en");
        Properties chinese = bundled("zh");
        assertEquals(english.stringPropertyNames(), chinese.stringPropertyNames());
        assertNotEquals(english.getProperty("binding-window-unavailable"),
                chinese.getProperty("binding-window-unavailable"));
        assertEquals(chinese.getProperty("binding-window-unavailable"),
                FabricMessages.load("zh", directory).getProperty("binding-window-unavailable"));
    }

    @Test
    void missingAndBlankOverrideKeysFallBack() throws Exception {
        Files.writeString(directory.resolve("messages_en.properties"),
                "binding-checking=Custom message\nbinding-login-required=\n", StandardCharsets.UTF_8);
        Properties messages = FabricMessages.load("en", directory);
        assertEquals("Custom message", messages.getProperty("binding-checking"));
        assertEquals(bundled("en").getProperty("binding-window-unavailable"),
                messages.getProperty("binding-window-unavailable"));
        assertEquals(bundled("en").getProperty("binding-login-required"),
                messages.getProperty("binding-login-required"));
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        assertEquals(FabricMessages.load("en", directory), FabricMessages.load("unsupported", directory));
    }

    @Test
    void missingChineseOverrideKeyFallsBackToEnglish() throws Exception {
        Files.writeString(directory.resolve("messages_zh.properties"),
                "binding-checking=Selected translation\n", StandardCharsets.UTF_8);
        Properties messages = FabricMessages.load("zh", directory);
        assertEquals("Selected translation", messages.getProperty("binding-checking"));
        assertEquals(bundled("en").getProperty("binding-window-unavailable"),
                messages.getProperty("binding-window-unavailable"));
    }

    private Properties bundled(String language) throws Exception {
        Properties values = new Properties();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/messages_" + language + ".properties"), StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        return values;
    }
}
