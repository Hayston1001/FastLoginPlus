/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LanguageFileUpdaterTest {
    @TempDir
    Path directory;

    private final ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);

    @Test
    void missingKeysUseChineseFirstAndEnglishOnlyForAbsentTranslations() throws Exception {
        Configuration english = provider.load("known: Old English\nadded: New English\nfallback: English only\n");
        Configuration chinese = provider.load("known: 中文旧值\nadded: 中文新增\n");
        Path file = directory.resolve("messages_zh.yml");
        String original = "_messages-version: 1\n# Keep this comment\nknown: '自定义提示'\n";
        Files.write(file, original.getBytes(StandardCharsets.UTF_8));
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, chinese, 1, null);
        assertEquals(2, result.added);
        assertEquals(0, result.refreshed);
        Configuration upgraded = read(file);
        assertEquals("中文新增", upgraded.getString("added"));
        assertEquals("English only", upgraded.getString("fallback"));
        assertEquals("自定义提示", upgraded.getString("known"));
        assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).startsWith(original));
    }

    @Test
    void onlyExactUntouchedEnglishValuesMigrateAndSecondRunDoesNotRewrite() throws Exception {
        Configuration english = provider.load("notice: English default\ncustom: English custom\n"
                + "blank: Default blank\nspaced: English spaced\nmultiline: English multiline\n");
        Configuration chinese = provider.load("notice: 中文提示\ncustom: 中文自定义\nblank: 中文空值\n"
                + "spaced: 中文空格\nmultiline: 中文多行\n");
        Path file = directory.resolve("messages_zh.yml");
        Files.write(file, ("notice: English default\ncustom: English custom!\nblank: ''\n"
                + "spaced: ' English spaced '\nmultiline: |\n  Custom first line\n  自定义第二行\n"
                + "obsolete-key: Retain unknown custom data\n").getBytes(StandardCharsets.UTF_8));
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, chinese, 1, provider.load("{}"));
        assertEquals(1, result.refreshed);
        assertEquals(0, result.added);
        Configuration upgraded = read(file);
        assertEquals("中文提示", upgraded.getString("notice"));
        assertEquals("English custom!", upgraded.getString("custom"));
        assertEquals("", upgraded.getString("blank"));
        assertEquals(" English spaced ", upgraded.getString("spaced"));
        assertEquals("Custom first line\n自定义第二行\n", upgraded.getString("multiline"));
        assertEquals("Retain unknown custom data", upgraded.getString("obsolete-key"));
        byte[] afterFirst = Files.readAllBytes(file);
        LanguageFileUpdater.Result repeated = LanguageFileUpdater.update(file, english, chinese, 1, null);
        assertEquals(0, repeated.added);
        assertEquals(0, repeated.refreshed);
        assertArrayEquals(afterFirst, Files.readAllBytes(file));
    }

    @Test
    void englishAndUnavailableTranslationsDoNotTriggerMigration() throws Exception {
        Configuration english = provider.load("notice: English default\n");
        Path file = directory.resolve("messages_en.yml");
        Files.write(file, "notice: English default\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, LanguageFileUpdater.update(file, english, english, 1, english).refreshed);
        assertEquals(0, LanguageFileUpdater.update(file, english, provider.load("{}"), 1, english).refreshed);
    }

    @Test
    @SuppressWarnings("unchecked")
    void productionUpgradeAppendsChineseAndLogsOneRefreshSummaryOnly() throws Exception {
        Configuration english = bundled("en");
        Configuration chinese = bundled("zh");
        Configuration user = new Configuration();
        user.set("database-error", english.getString("database-error"));
        user.set("premium-policy-disabled", english.getString("premium-policy-disabled"));
        user.set("auto-login", "My custom login notice");
        Path file = directory.resolve("messages_zh.yml");
        try (java.io.Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            provider.save(user, writer);
        }
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        Logger log = mock(Logger.class);
        when(plugin.getPluginFolder()).thenReturn(directory);
        when(plugin.getLog()).thenReturn(log);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);
        Method upgrade = FastLoginCore.class.getDeclaredMethod("appendMissingKeys", String.class, String.class);
        upgrade.setAccessible(true);
        upgrade.invoke(core, "messages_zh.yml", "messages_en.yml");
        byte[] first = Files.readAllBytes(file);
        upgrade.invoke(core, "messages_zh.yml", "messages_en.yml");
        assertArrayEquals(first, Files.readAllBytes(file));
        Configuration upgraded = read(file);
        assertEquals(chinese.getString("database-error"), upgraded.getString("database-error"));
        assertEquals(chinese.getString("premium-policy-disabled"), upgraded.getString("premium-policy-disabled"));
        assertEquals(chinese.getString("premium-password-login"), upgraded.getString("premium-password-login"));
        assertEquals("My custom login notice", upgraded.getString("auto-login"));
        assertEquals(1, upgraded.getInt("_messages-version"));
        verify(log).info("Refreshed {} unchanged message values to messages v{} in {}",
                2, 1, "messages_zh.yml");
        verify(log).info("Appended {} missing keys to {}", english.getKeys().size() - 4, "messages_zh.yml");
        verifyNoMoreInteractions(log);
    }

    @Test
    void archivedZeroChineseValuesAndLeakedEnglishRefreshButCustomAndUnknownKeysStay() throws Exception {
        Configuration english = bundled("en");
        Configuration chinese = bundled("zh");
        Configuration oldChinese = resource("messages/archive/0/messages_zh.yml");
        Path file = directory.resolve("messages_zh.yml");
        Configuration user = new Configuration();
        user.set("wait-on-proxy", oldChinese.get("wait-on-proxy"));
        user.set("premium-password-login", english.get("premium-password-login"));
        user.set("auto-login", "Customized login");
        user.set("obsolete", "Keep unknown");
        save(file, user);
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, chinese, 1, oldChinese);
        assertEquals(2, result.refreshed);
        Configuration upgraded = read(file);
        for (String key : new String[]{"wait-on-proxy", "ownership-conflict", "premium-password-login"}) {
            assertEquals(chinese.get(key), upgraded.get(key), key);
        }
        assertEquals("Customized login", upgraded.get("auto-login"));
        assertEquals("Keep unknown", upgraded.get("obsolete"));
        assertEquals(1, upgraded.getInt("_messages-version"));
    }

    @Test
    void versionOneUpgradeRefreshesOnlyOldDefaultsAndPreservesMultilineAndReservedValues() throws Exception {
        Configuration english = provider.load("_messages-version: 2\nnotice: English new\nadded: English added\n"
                + "custom: English custom\nmultiline: English multiline\n_metadata: ignored\n");
        Configuration localized = provider.load("_messages-version: 2\nnotice: 新值\nadded: 新增\ncustom: 默认\n"
                + "multiline: |\n  第一行\n  第二行\n_metadata: 42\n");
        Configuration previous = provider.load("notice: 旧值\ncustom: 旧自定义\nmultiline: Old multiline\n");
        Path file = directory.resolve("messages_zh.yml");
        Files.write(file, ("_messages-version: 1\nnotice: 旧值\ncustom: 自定义\nmultiline: Old multiline\n"
                + "extra: |\n  Keep first\n  Keep second\n_private: Keep reserved\n").getBytes(StandardCharsets.UTF_8));
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, localized, 2, previous);
        assertEquals(1, result.added);
        assertEquals(2, result.refreshed);
        Configuration upgraded = read(file);
        assertEquals("新值", upgraded.get("notice"));
        assertEquals("新增", upgraded.get("added"));
        assertEquals("自定义", upgraded.get("custom"));
        assertEquals("第一行\n第二行\n", upgraded.get("multiline"));
        assertEquals("Keep first\nKeep second\n", upgraded.get("extra"));
        assertEquals("Keep reserved", upgraded.get("_private"));
        assertFalse(upgraded.getKeys().contains("_metadata"));
        assertEquals(2, upgraded.getInt("_messages-version"));
        byte[] first = Files.readAllBytes(file);
        java.nio.file.attribute.FileTime timestamp = java.nio.file.attribute.FileTime.fromMillis(1000);
        Files.setLastModifiedTime(file, timestamp);
        LanguageFileUpdater.Result repeated = LanguageFileUpdater.update(file, english, localized, 2, previous);
        assertEquals(0, repeated.added);
        assertEquals(0, repeated.refreshed);
        assertArrayEquals(first, Files.readAllBytes(file));
        assertEquals(timestamp, Files.getLastModifiedTime(file));
    }

    @Test
    void equalOrHigherUserVersionNeverRefreshesOrDowngradesAndOnlyAppends() throws Exception {
        Configuration english = provider.load("_messages-version: 2\nnotice: New\nadded: Added\n_metadata: ignored\n");
        Configuration previous = provider.load("notice: Old\n");
        for (int version : new int[]{2, 3}) {
            Path file = directory.resolve("messages_" + version + ".yml");
            String original = "# Retain comments\n_messages-version: " + version + "\nnotice: Old\n";
            Files.write(file, original.getBytes(StandardCharsets.UTF_8));
            LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, english, 2, previous);
            assertEquals(1, result.added);
            assertEquals(0, result.refreshed);
            assertEquals("Old", read(file).get("notice"));
            assertEquals(version, read(file).getInt("_messages-version"));
            assertFalse(read(file).getKeys().contains("_metadata"));
            assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).startsWith(original));
        }
    }

    @Test
    void missingSnapshotWritesMarkerButNeverRefreshesEvenForVersionZero() throws Exception {
        Configuration english = provider.load("notice: English default\n");
        Configuration chinese = provider.load("notice: 中文\n");
        for (String marker : new String[]{"", "_messages-version: 1\n"}) {
            Path file = directory.resolve("messages.yml");
            Files.write(file, (marker + "notice: English default\n").getBytes(StandardCharsets.UTF_8));
            LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, chinese, 2, null);
            assertEquals(0, result.added);
            assertEquals(0, result.refreshed);
            assertEquals("English default", read(file).get("notice"));
            assertEquals(2, read(file).getInt("_messages-version"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingSnapshotWarnsOnceAndOnlyAppendsBeforeAdvancingVersion() throws Exception {
        Path file = directory.resolve("messages_zh.yml");
        Files.write(file, "_messages-version: 12345\nnotice: English default\n".getBytes(StandardCharsets.UTF_8));
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        Logger log = mock(Logger.class);
        when(plugin.getPluginFolder()).thenReturn(directory);
        when(plugin.getLog()).thenReturn(log);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);
        Configuration english = provider.load("_messages-version: 12346\nnotice: English default\nadded: Added\n");
        Configuration localized = provider.load("_messages-version: 12346\nnotice: 中文\nadded: 新增\n");
        Method upgrade = FastLoginCore.class.getDeclaredMethod("updateLanguageFile", String.class, String.class,
                Configuration.class, Configuration.class);
        upgrade.setAccessible(true);
        upgrade.invoke(core, "messages_zh.yml", "messages_en.yml", english, localized);
        byte[] first = Files.readAllBytes(file);
        upgrade.invoke(core, "messages_zh.yml", "messages_en.yml", english, localized);
        assertArrayEquals(first, Files.readAllBytes(file));
        assertEquals(12346, read(file).getInt("_messages-version"));
        assertEquals("English default", read(file).get("notice"));
        assertEquals("新增", read(file).get("added"));
        verify(log).warn("Cannot refresh {}: no snapshot for messages v{}", "messages_zh.yml", 12345);
        verify(log).info("Appended {} missing keys to {}", 1, "messages_zh.yml");
        verifyNoMoreInteractions(log);
    }

    @Test
    @SuppressWarnings("unchecked")
    void archiveLookupFallsBackToEnglishAndReturnsNullForUnknownVersions() throws Exception {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);
        Method lookup = FastLoginCore.class.getDeclaredMethod("loadMessageSnapshot",
                ConfigurationProvider.class, int.class, String.class, String.class);
        lookup.setAccessible(true);
        assertNull(lookup.invoke(core, provider, 12345, "messages_zh.yml", "messages_en.yml"));
        Configuration fallback = (Configuration) lookup.invoke(core, provider, 0,
                "messages_unknown.yml", "messages_en.yml");
        assertEquals(resource("messages/archive/0/messages_en.yml").get("auto-login"), fallback.get("auto-login"));
    }

    @Test
    void missingAndNonIntegerMarkersAreVersionZero() throws Exception {
        for (String yaml : new String[]{"{}", "_messages-version: invalid", "_messages-version: '1'",
                "_messages-version: 1.5", "_messages-version: true", "_messages-version: null"}) {
            assertEquals(0, LanguageFileUpdater.version(provider.load(yaml)), yaml);
        }
        assertEquals(2, LanguageFileUpdater.version(provider.load("_messages-version: 2")));
    }

    @Test
    void versionOneDoesNotAcceptEnglishLeakOrValuesMissingFromTheOldSnapshot() throws Exception {
        Configuration english = provider.load("notice: English\nunknown: New default\nnull-value: New\n");
        Configuration localized = provider.load("notice: 中文\nunknown: 新值\nnull-value: 新值\n");
        Configuration previous = provider.load("notice: 旧值\n");
        Path file = directory.resolve("messages.yml");
        Files.write(file, ("_messages-version: 1\nnotice: English\nunknown: My value\nnull-value: null\n")
                .getBytes(StandardCharsets.UTF_8));
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, localized, 2, previous);
        assertEquals(0, result.refreshed);
        assertEquals(0, result.added);
        assertEquals("English", read(file).get("notice"));
        assertEquals("My value", read(file).get("unknown"));
        assertNull(read(file).get("null-value"));
    }

    private void save(Path file, Configuration messages) throws Exception {
        try (java.io.Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            provider.save(messages, writer);
        }
    }

    private Configuration read(Path file) throws Exception {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return provider.load(reader);
        }
    }

    private Configuration bundled(String language) throws Exception {
        return resource("messages_" + language + ".yml");
    }

    private Configuration resource(String path) throws Exception {
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/" + path), StandardCharsets.UTF_8)) {
            return provider.load(reader);
        }
    }
}
