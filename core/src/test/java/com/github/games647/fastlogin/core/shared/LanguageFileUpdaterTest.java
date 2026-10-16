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
        String original = "# Keep this comment\nknown: '自定义提示'\n";
        Files.write(file, original.getBytes(StandardCharsets.UTF_8));
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, chinese);
        assertEquals(2, result.added);
        assertEquals(0, result.migrated);
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
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(file, english, chinese);
        assertEquals(1, result.migrated);
        assertEquals(0, result.added);
        Configuration upgraded = read(file);
        assertEquals("中文提示", upgraded.getString("notice"));
        assertEquals("English custom!", upgraded.getString("custom"));
        assertEquals("", upgraded.getString("blank"));
        assertEquals(" English spaced ", upgraded.getString("spaced"));
        assertEquals("Custom first line\n自定义第二行\n", upgraded.getString("multiline"));
        assertEquals("Retain unknown custom data", upgraded.getString("obsolete-key"));
        byte[] afterFirst = Files.readAllBytes(file);
        LanguageFileUpdater.Result repeated = LanguageFileUpdater.update(file, english, chinese);
        assertEquals(0, repeated.added);
        assertEquals(0, repeated.migrated);
        assertArrayEquals(afterFirst, Files.readAllBytes(file));
    }

    @Test
    void englishAndUnavailableTranslationsDoNotTriggerMigration() throws Exception {
        Configuration english = provider.load("notice: English default\n");
        Path file = directory.resolve("messages_en.yml");
        Files.write(file, "notice: English default\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, LanguageFileUpdater.update(file, english, english).migrated);
        assertEquals(0, LanguageFileUpdater.update(file, english, provider.load("{}")).migrated);
    }

    @Test
    @SuppressWarnings("unchecked")
    void productionUpgradeAppendsChineseAndLogsOneMigrationSummaryOnly() throws Exception {
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
        verify(log).info("Migrated {} unchanged English message values to bundled translations in {}",
                2, "messages_zh.yml");
        verify(log).info("Appended {} missing keys to {}", english.getKeys().size() - 3, "messages_zh.yml");
        verifyNoMoreInteractions(log);
    }

    private Configuration read(Path file) throws Exception {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return provider.load(reader);
        }
    }

    private Configuration bundled(String language) throws Exception {
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/messages_" + language + ".yml"), StandardCharsets.UTF_8)) {
            return provider.load(reader);
        }
    }
}
