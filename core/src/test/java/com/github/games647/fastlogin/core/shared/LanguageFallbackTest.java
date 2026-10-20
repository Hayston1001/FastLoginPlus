/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LanguageFallbackTest {

    @TempDir
    Path directory;

    @Test
    void invalidUserLanguageFallsBackToBundledLanguageAndLogs() throws Exception {
        Path file = directory.resolve("messages_zh.yml");
        byte[] invalid = "premium-warning: 'unterminated\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, invalid);
        Logger log = mock(Logger.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = core(log);
        Method append = FastLoginCore.class.getDeclaredMethod("appendMissingKeys", String.class, String.class);
        append.setAccessible(true);

        assertDoesNotThrow(() -> append.invoke(core, "messages_zh.yml", "messages_en.yml"));
        assertDoesNotThrow(() -> core.loadLanguageMessages("messages_zh.yml", "messages_en.yml"));

        assertNotNull(core.getMessage("ownership-conflict"));
        assertTrue(core.getMessage("ownership-conflict").contains("此连接不符合"));
        assertArrayEquals(invalid, Files.readAllBytes(file));
        verify(log).error(contains("Invalid language file"), eq(file), any(Throwable.class));
        verify(log).warn("Loaded bundled language fallback for {}", file);
    }

    @Test
    void unavailableBundledSelectionFallsBackToEnglishWithoutChangingTheOverride() throws Exception {
        Path file = directory.resolve("messages_unknown.yml");
        byte[] invalid = "notice: [unterminated\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, invalid);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = core(mock(Logger.class));

        assertDoesNotThrow(() -> core.loadLanguageMessages("messages_unknown.yml", "messages_en.yml"));

        assertTrue(core.getMessage("ownership-conflict").contains("Account ownership does not match"));
        assertArrayEquals(invalid, Files.readAllBytes(file));
    }

    @Test
    void bundledSelectedFallbackEnumeratesEnglishOnlyKeys() throws Exception {
        Files.write(directory.resolve("messages_fallback_partial.yml"),
                "selected: [unterminated\n".getBytes(StandardCharsets.UTF_8));
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = core(mock(Logger.class));

        core.loadLanguageMessages("messages_fallback_partial.yml", "messages_fallback_en.yml");

        assertEquals("Selected bundled", core.getMessage("selected"));
        assertEquals("English only", core.getMessage("english-only"));
    }

    @Test
    void repeatedLoadsReplaceOldEntriesAndInvalidMessageTypesUseFallback() throws Exception {
        Path file = directory.resolve("messages_en.yml");
        Logger log = mock(Logger.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = core(log);
        Files.write(file, "custom: User entry\n".getBytes(StandardCharsets.UTF_8));
        core.loadLanguageMessages("messages_en.yml", "messages_en.yml");
        assertEquals("User entry", core.getMessage("custom"));

        Files.write(file, "custom: ''\n".getBytes(StandardCharsets.UTF_8));
        core.loadLanguageMessages("messages_en.yml", "messages_en.yml");
        assertNull(core.getMessage("custom"));

        byte[] invalid = "partial: Must not leak\npremium-warning: 42\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, invalid);
        assertDoesNotThrow(() -> core.loadLanguageMessages("messages_en.yml", "messages_en.yml"));

        assertNull(core.getMessage("partial"));
        assertNull(core.getMessage("custom"));
        assertNotNull(core.getMessage("premium-warning"));
        assertArrayEquals(invalid, Files.readAllBytes(file));
        verify(log).error(contains("Invalid language file"), eq(file), any(Throwable.class));
        verify(log).warn("Loaded bundled language fallback for {}", file);
    }

    @Test
    void missingFileUsesEnglishDefaultsWithoutCreatingAFile() {
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = core(mock(Logger.class));

        core.loadLanguageMessages("messages_zh.yml", "messages_en.yml");

        assertTrue(core.getMessage("ownership-conflict").contains("Account ownership does not match"));
        assertFalse(Files.exists(directory.resolve("messages_zh.yml")));
    }

    @Test
    void failedBundledLoadDoesNotLeaveStaleMessages() throws Exception {
        Logger log = mock(Logger.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = core(log);
        core.loadLanguageMessages("messages_en.yml", "messages_en.yml");
        assertNotNull(core.getMessage("ownership-conflict"));

        assertDoesNotThrow(() -> core.loadLanguageMessages("messages_en.yml", "missing-bundle.yml"));

        assertNull(core.getMessage("ownership-conflict"));
        verify(log).error(contains("Failed to load bundled language messages"),
                eq(directory.resolve("messages_en.yml")), any(Throwable.class));
    }

    @SuppressWarnings("unchecked")
    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core(Logger log) {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getPluginFolder()).thenReturn(directory);
        when(plugin.getLog()).thenReturn(log);
        return new FastLoginCore<>(plugin);
    }
}
