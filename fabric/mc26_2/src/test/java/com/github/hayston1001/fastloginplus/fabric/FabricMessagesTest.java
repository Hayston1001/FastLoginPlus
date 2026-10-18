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
            assertEquals(bundled(effective).getProperty("binding-checking"),
                    loaded.getProperty("binding-checking"));
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
        assertNotEquals(english.getProperty("binding-window-unavailable"),
                chinese.getProperty("binding-window-unavailable"));
        assertEquals(chinese.getProperty("binding-window-unavailable"),
                FabricMessages.load("zh", directory).getProperty("binding-window-unavailable"));
        assertNotEquals(english.getProperty("binding-consent-required"),
                chinese.getProperty("binding-consent-required"));
        assertEquals(chinese.getProperty("binding-consent-required"),
                FabricMessages.load("zh", directory).getProperty("binding-consent-required"));
        org.junit.jupiter.api.Assertions.assertTrue(english.getProperty("premium-policy-disabled")
                .contains("no usable password"));
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
    void queuedBindingTellsThePlayerToReconnectManuallyInBothLanguages() throws Exception {
        String english = bundled("en").getProperty("binding-queued");
        String chinese = bundled("zh").getProperty("binding-queued");
        assertTrue(english.contains("consent recorded for UUID {uuid}. Please reconnect"));
        assertTrue(chinese.contains("同意已记录, 目标 UUID 为 {uuid}. 请重新连接"));
        assertTrue(english.contains("Your password stays unchanged until success."));
        assertTrue(chinese.contains("成功前原密码不变."));
    }

    @Test
    void missingAndBlankOverrideKeysFallBack() throws Exception {
        Files.writeString(directory.resolve("messages_en.properties"),
                "binding-checking=Custom message\nbinding-login-required=\nbinding-consent-required=\n",
                StandardCharsets.UTF_8);
        Properties messages = FabricMessages.load("en", directory);
        assertEquals("Custom message", messages.getProperty("binding-checking"));
        assertEquals(bundled("en").getProperty("binding-window-unavailable"),
                messages.getProperty("binding-window-unavailable"));
        assertEquals(bundled("en").getProperty("binding-login-required"),
                messages.getProperty("binding-login-required"));
        assertEquals(bundled("en").getProperty("binding-consent-required"),
                messages.getProperty("binding-consent-required"));
        assertEquals(bundled("en").getProperty("same-name-cracked-recorded"),
                messages.getProperty("same-name-cracked-recorded"));
        assertEquals(bundled("en").getProperty("premium-policy-disabled"),
                messages.getProperty("premium-policy-disabled"));
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
        assertEquals(bundled("en").getProperty("binding-consent-required"),
                messages.getProperty("binding-consent-required"));
        assertEquals(bundled("en").getProperty("same-name-cracked-recorded"),
                messages.getProperty("same-name-cracked-recorded"));
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
