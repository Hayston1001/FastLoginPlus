/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.hayston1001.fastloginplus.shared.message.LanguageMessages;

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
    void passwordExplanationAndPolicyDefaultsMatchTheApprovedText() throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        String[] languages = {"en", "zh"};
        String[] passwordMessages = {
                "&eYour premium identity was verified. This account already has a password, "
                        + "so passwordless login is skipped for this session. After logging in you may run "
                        + "&b/flp premium&e to enable passwordless login.",
                "&e正版身份已验证. 该账号已存在密码, 本次不进行免密登录. "
                        + "可选择登录后执行 &b/flp premium&e 以启用免密登录"
        };
        String[] policyMessages = {
                "&eThe server has disabled automatic premium login. This premium-owned account has no usable "
                        + "password. Contact an administrator or reset the password according to the server policy.",
                "&e服务器已关闭正版自动登录. 此已转正账号没有可用密码, 请联系管理员或按服务器策略重新设置密码."
        };
        for (int index = 0; index < languages.length; index++) {
            try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/messages_" + languages[index] + ".yml"), StandardCharsets.UTF_8)) {
                Configuration messages = provider.load(reader);
                assertEquals(passwordMessages[index], messages.getString("premium-password-login"));
                assertEquals(policyMessages[index], messages.getString("premium-policy-disabled"));
            }
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
    void premiumPolicyNoticeExistsInBothLanguagesAndOldOverridesFallBackToEnglish() throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        try (InputStreamReader english = new InputStreamReader(getClass().getResourceAsStream(
                "/messages_en.yml"), StandardCharsets.UTF_8);
             InputStreamReader chinese = new InputStreamReader(getClass().getResourceAsStream(
                     "/messages_zh.yml"), StandardCharsets.UTF_8)) {
            Configuration defaults = provider.load(english);
            String explanation = defaults.getString("premium-policy-disabled");
            assertTrue(explanation.contains("no usable password"));
            assertFalse(provider.load(chinese).getString("premium-policy-disabled").isEmpty());
            Path file = folder.resolve("messages_zh.yml");
            Files.write(file, "premium-password-login: Existing translation\n".getBytes(StandardCharsets.UTF_8));
            Configuration loaded = LanguageMessages.load(file, defaults, FastLoginCore.configurationLoader(provider));
            assertEquals(explanation, loaded.getString("premium-policy-disabled"));
            assertEquals("Existing translation", loaded.getString("premium-password-login"));
        }
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

    @Test
    void reservedPrefixNeverBecomesAPlayerMessageEvenWithNonStringMetadata() throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        Configuration raw = provider.load("_messages-version: 1\n_internal: 42\n_label: hidden\nnotice: Hello\n");
        Map<String, String> messages = new HashMap<>();
        LanguageMessages.populate(raw.getKeys(), raw::get, messages);
        assertEquals(java.util.Collections.singletonMap("notice", "Hello"), messages);
    }
}
