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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMessagesTest {
    @TempDir
    Path directory;

    @Test
    void startupReportsEffectiveLanguageAndTheSettingToChange() throws Exception {
        for (String requested : new String[]{"en", "EN", "zh", "ZH", "", null}) {
            String effective = "zh".equalsIgnoreCase(requested) ? "zh" : "en";
            org.slf4j.Logger logger = org.mockito.Mockito.mock(org.slf4j.Logger.class);
            Properties loaded = FabricMessages.loadAndReportLanguage(requested, directory, logger);
            assertEquals(bundled(effective).getProperty("premium-requested"),
                    loaded.getProperty("premium-requested"));
            org.mockito.Mockito.verify(logger).info("Fabric message language={} "
                    + "(set language in fastloginplus-fabric.yml; restart the backend to apply changes)",
                    effective);
            org.mockito.Mockito.verifyNoMoreInteractions(logger);
        }
    }

    @Test
    void unknownLanguageWarnsOnceAndLoadsEnglishMessages() throws Exception {
        org.slf4j.Logger logger = org.mockito.Mockito.mock(org.slf4j.Logger.class);
        Properties loaded = FabricMessages.loadAndReportLanguage("unsupported", directory, logger);
        assertEquals(bundled("en"), loaded);
        org.mockito.Mockito.verify(logger).warn(
                "Unknown Fabric message language='{}'; effective language={}", "unsupported", "en");
        org.mockito.Mockito.verify(logger).info("Fabric message language={} "
                + "(set language in fastloginplus-fabric.yml; restart the backend to apply changes)", "en");
        org.mockito.Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    void bundledConfigurationParsesWithUnchangedLocalDefaults() throws Exception {
        FlatYamlReader.Result configuration = FlatYamlReader.read(FabricConfig.templateLines());
        assertEquals(0, configuration.unsupportedLine());
        assertEquals(java.util.Set.of("language", "allowed-proxy-ids"), configuration.values().keySet());
        assertEquals("en", configuration.values().get("language"));
        assertEquals("", configuration.values().get("allowed-proxy-ids"));
    }

    @Test
    void bundledLanguagesHaveIdenticalKeysAndChineseIsUtf8() throws Exception {
        Properties english = bundled("en");
        Properties chinese = bundled("zh");
        assertEquals(english.stringPropertyNames(), chinese.stringPropertyNames());
        assertNotEquals(english.getProperty("premium-requested"), chinese.getProperty("premium-requested"));
        assertEquals(chinese.getProperty("premium-requested"),
                FabricMessages.load("zh", directory).getProperty("premium-requested"));
        assertNotEquals(english.getProperty("binding-consent-required"),
                chinese.getProperty("binding-consent-required"));
        assertEquals(chinese.getProperty("binding-consent-required"),
                FabricMessages.load("zh", directory).getProperty("binding-consent-required"));
        org.junit.jupiter.api.Assertions.assertTrue(english.getProperty("premium-policy-disabled")
                .contains("retained password does not enable unverified access"));
        assertNotEquals(english.getProperty("premium-policy-disabled"),
                chinese.getProperty("premium-policy-disabled"));
        assertEquals("你的账号已被记录为盗版. 你是正版玩家吗? 如被误判可使用 /flp premium 重新发起验证",
                chinese.getProperty("same-name-cracked-recorded"));
        org.junit.jupiter.api.Assertions.assertTrue(english.getProperty("same-name-cracked-recorded")
                .contains("/flp premium"));
    }

    @Test
    void bedrockRefusalIsPresentAndHasNoPlaceholdersInBothLanguages() throws Exception {
        String english = bundled("en").getProperty("bedrock-unsupported");
        String chinese = bundled("zh").getProperty("bedrock-unsupported");
        assertTrue(english.contains("Fabric mode does not support Bedrock/Floodgate"));
        assertTrue(english.contains("Java Edition"));
        assertTrue(chinese.contains("Fabric 模式不支持基岩版/Floodgate"));
        assertTrue(chinese.contains("Java 版"));
        for (String message : new String[]{english, chinese}) {
            assertTrue(!message.contains("{") && !message.contains("}") && !message.contains("%"));
        }
    }

    @Test
    void premiumRequestExplainsConfirmationReconnectAndDormantPasswordInBothLanguages() throws Exception {
        String english = bundled("en").getProperty("premium-requested");
        String chinese = bundled("zh").getProperty("premium-requested");
        assertTrue(english.contains("premium consent requested."));
        assertTrue(chinese.contains("正版同意请求已发出."));
        assertTrue(english.contains("If a confirmation prompt appears, run /flp premium once more."));
        assertTrue(chinese.contains("若出现确认提示, 请再执行一次 /flp premium."));
        assertTrue(english.contains("Then reconnect with your premium account to enable passwordless login."));
        assertTrue(chinese.contains("然后使用正版账号重新连接以启用免密登录."));
        assertTrue(english.contains("Your password is retained but not used while premium is enabled."));
        assertTrue(chinese.contains("密码保留, 但 premium 期间不使用."));
        assertFalse(english.contains("consent recorded"));
        assertFalse(chinese.contains("同意已记录"));
        for (String language : new String[]{"en", "zh"}) {
            assertFalse(bundled(language).containsKey("binding-queued"));
            assertFalse(bundled(language).containsKey("binding-complete"));
            for (String key : new String[]{"binding-account-saving", "binding-account-changed", "binding-checking",
                    "binding-name-unavailable", "binding-requested", "binding-window-unavailable",
                    "admin-premium-queued", "admin-premium-name-unavailable", "admin-premium-changed"}) {
                assertFalse(bundled(language).containsKey(key), key);
            }
        }
    }

    @Test
    void adminMessagesExplainTheRequestAndRemainReadableInBothLanguages() throws Exception {
        for (String language : new String[]{"en", "zh"}) {
            Properties messages = bundled(language);
            for (String suffix : new String[]{"permission", "no-carrier", "already", "open", "requested"}) {
                String key = "admin-premium-" + suffix;
                String value = messages.getProperty(key);
                assertTrue(value != null && !value.isBlank(), key);
                assertFalse(value.contains("\n") || value.contains("\r"), key);
                assertFalse(value.matches(".*[\\uFF0C\\u3002\\uFF1B\\uFF1A\\uFF01\\uFF1F\\uFF08\\uFF09].*"), key);
                assertEquals(value, FabricMessages.load(language, directory).getProperty(key));
                assertEquals(!java.util.Set.of("permission", "no-carrier").contains(suffix),
                        value.contains("{player}"), key);
                assertFalse(value.contains("{uuid}"), key);
            }
            String requested = messages.getProperty("admin-premium-requested");
            assertTrue(requested.contains("reconnect manually") || requested.contains("手动重连"));
            assertTrue(requested.contains("password is retained but not used")
                    || requested.contains("密码保留, 但 premium 期间不使用"));
            assertFalse(requested.contains("ten minutes") || requested.contains("十分钟"));
        }
    }

    @Test
    void missingAndBlankOverrideKeysFallBack() throws Exception {
        Files.writeString(directory.resolve("messages_en.properties"),
                "premium-requested=Custom message\nbinding-login-required=\nbinding-consent-required=\n",
                StandardCharsets.UTF_8);
        Properties messages = FabricMessages.load("en", directory);
        assertEquals("Custom message", messages.getProperty("premium-requested"));
        assertEquals(bundled("en").getProperty("binding-login-required"),
                messages.getProperty("binding-login-required"));
        assertEquals(bundled("en").getProperty("binding-consent-required"),
                messages.getProperty("binding-consent-required"));
        assertEquals(bundled("en").getProperty("same-name-cracked-recorded"),
                messages.getProperty("same-name-cracked-recorded"));
        assertEquals(bundled("en").getProperty("premium-policy-disabled"),
                messages.getProperty("premium-policy-disabled"));
        assertEquals(bundled("en").getProperty("admin-premium-requested"),
                messages.getProperty("admin-premium-requested"));
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        assertEquals(FabricMessages.load("en", directory), FabricMessages.load("unsupported", directory));
    }

    @Test
    void missingChineseOverrideKeyFallsBackToEnglish() throws Exception {
        Files.writeString(directory.resolve("messages_zh.properties"),
                "admin-premium-requested=Selected translation\n", StandardCharsets.UTF_8);
        Properties messages = FabricMessages.load("zh", directory);
        assertEquals("Selected translation", messages.getProperty("admin-premium-requested"));
        assertEquals(bundled("en").getProperty("binding-consent-required"),
                messages.getProperty("binding-consent-required"));
        assertEquals(bundled("en").getProperty("same-name-cracked-recorded"),
                messages.getProperty("same-name-cracked-recorded"));
        assertEquals(bundled("en").getProperty("premium-requested"), messages.getProperty("premium-requested"));
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
