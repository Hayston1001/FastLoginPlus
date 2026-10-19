/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMessagesTest {
    @TempDir
    Path directory;

    @Test
    void startupReportsEffectiveLanguageAndTheSettingToChange() throws Exception {
        for (String requested : new String[]{"en", "EN", "zh", "ZH", "", null}) {
            String effective = "zh".equalsIgnoreCase(requested) ? "zh" : "en";
            org.slf4j.Logger logger = org.mockito.Mockito.mock(org.slf4j.Logger.class);
            Map<String, String> loaded = FabricMessages.loadAndReportLanguage(requested, directory, logger);
            assertEquals(bundled(effective).get("premium-requested"),
                    loaded.get("premium-requested"));
            org.mockito.Mockito.verify(logger).info("Fabric message language={} "
                    + "(set language in fastloginplus-fabric.yml; restart the backend to apply changes)",
                    effective);
            org.mockito.Mockito.verifyNoMoreInteractions(logger);
        }
    }

    @Test
    void unknownLanguageWarnsOnceAndLoadsEnglishMessages() throws Exception {
        org.slf4j.Logger logger = org.mockito.Mockito.mock(org.slf4j.Logger.class);
        Map<String, String> loaded = FabricMessages.loadAndReportLanguage("unsupported", directory, logger);
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
        Map<String, String> english = bundled("en");
        Map<String, String> chinese = bundled("zh");
        assertEquals(english.keySet(), chinese.keySet());
        assertNotEquals(english.get("premium-requested"), chinese.get("premium-requested"));
        assertEquals(chinese.get("premium-requested"),
                FabricMessages.load("zh", directory).get("premium-requested"));
        assertNotEquals(english.get("binding-consent-required"),
                chinese.get("binding-consent-required"));
        assertEquals(chinese.get("binding-consent-required"),
                FabricMessages.load("zh", directory).get("binding-consent-required"));
        assertTrue(english.get("premium-policy-disabled")
                .contains("retained password does not enable unverified access"));
        assertNotEquals(english.get("premium-policy-disabled"),
                chinese.get("premium-policy-disabled"));
        assertEquals("你的账号已被记录为盗版. 你是正版玩家吗? 如被误判可使用 /flp premium 重新发起验证",
                chinese.get("same-name-cracked-recorded"));
        assertTrue(english.get("same-name-cracked-recorded").contains("/flp premium"));
    }

    @Test
    void bedrockRefusalIsPresentAndHasNoPlaceholdersInBothLanguages() throws Exception {
        String english = bundled("en").get("bedrock-unsupported");
        String chinese = bundled("zh").get("bedrock-unsupported");
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
        String english = bundled("en").get("premium-requested");
        String chinese = bundled("zh").get("premium-requested");
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
            Map<String, String> messages = bundled(language);
            for (String suffix : new String[]{"permission", "no-carrier", "already", "open", "requested"}) {
                String key = "admin-premium-" + suffix;
                String value = messages.get(key);
                assertTrue(value != null && !value.isBlank(), key);
                assertFalse(value.contains("\n") || value.contains("\r"), key);
                assertFalse(value.matches(".*[\\uFF0C\\u3002\\uFF1B\\uFF1A\\uFF01\\uFF1F\\uFF08\\uFF09].*"), key);
                assertEquals(value, FabricMessages.load(language, directory).get(key));
                assertEquals(!java.util.Set.of("permission", "no-carrier").contains(suffix),
                        value.contains("{player}"), key);
                assertFalse(value.contains("{uuid}"), key);
            }
            String requested = messages.get("admin-premium-requested");
            assertTrue(requested.contains("reconnect manually") || requested.contains("手动重连"));
            assertTrue(requested.contains("password is retained but not used")
                    || requested.contains("密码保留, 但 premium 期间不使用"));
            assertFalse(requested.contains("ten minutes") || requested.contains("十分钟"));
        }
    }

    @Test
    void crackedAndDeleteMessagesKeepTheirScopeAndEnglishPunctuation() throws Exception {
        for (String language : new String[]{"en", "zh"}) {
            Map<String, String> messages = bundled(language);
            for (String key : new String[]{"cracked-requested", "admin-cracked-requested", "cracked-no-password",
                    "delete-requested", "delete-premium-denied", "admin-command-permission",
                    "cracked-login-required", "cracked-save-failed"}) {
                String value = messages.get(key);
                assertTrue(value != null && !value.isBlank(), key);
                assertFalse(value.matches(".*[\uFF0C\u3002\uFF1B\uFF1A\uFF01\uFF1F\uFF08\uFF09].*"), key);
                assertEquals(value, FabricMessages.load(language, directory).get(key));
                assertEquals(java.util.Set.of("admin-cracked-requested", "cracked-no-password",
                        "delete-requested", "delete-premium-denied").contains(key), value.contains("{player}"));
            }
            String cracked = messages.get("cracked-requested");
            assertTrue(cracked.contains("reconnect manually") || cracked.contains("手动重连"));
            assertTrue(cracked.contains("password is retained") || cracked.contains("密码保留"));
            String delete = messages.get("delete-requested");
            assertTrue(delete.contains("proxy console") || delete.contains("代理控制台"));
            assertTrue(delete.contains("not deleted") || delete.contains("不删除 EasyAuth"));
        }
    }

    @Test
    void missingAndBlankOverrideKeysFallBack() throws Exception {
        Files.writeString(directory.resolve("messages_en.yml"),
                "premium-requested: \"Custom message\"\n"
                        + "binding-login-required: \"\"\n"
                        + "binding-consent-required: \"\"\n",
                StandardCharsets.UTF_8);
        Map<String, String> messages = FabricMessages.load("en", directory);
        assertEquals("Custom message", messages.get("premium-requested"));
        assertEquals(bundled("en").get("binding-login-required"),
                messages.get("binding-login-required"));
        assertEquals(bundled("en").get("binding-consent-required"),
                messages.get("binding-consent-required"));
        assertEquals(bundled("en").get("same-name-cracked-recorded"),
                messages.get("same-name-cracked-recorded"));
        assertEquals(bundled("en").get("premium-policy-disabled"),
                messages.get("premium-policy-disabled"));
        assertEquals(bundled("en").get("admin-premium-requested"),
                messages.get("admin-premium-requested"));
    }

    @Test
    void legacyPropertyOverridesAreMigratedToYaml() throws Exception {
        Files.writeString(directory.resolve("messages_en.properties"),
                "premium-requested=Legacy message\n", StandardCharsets.UTF_8);

        Map<String, String> messages = FabricMessages.load("en", directory);

        assertEquals("Legacy message", messages.get("premium-requested"));
        assertTrue(Files.exists(directory.resolve("messages_en.yml")));
        assertTrue(Files.exists(directory.resolve("messages_en.properties.bak")));
        assertFalse(Files.exists(directory.resolve("messages_en.properties")));
    }

    @Test
    void legacyPropertyMigrationPreservesEscapes() throws Exception {
        Files.writeString(directory.resolve("messages_en.properties"),
                "premium-requested=Line\\nQuote \\\"Q\\\" and \\\\ slash\n", StandardCharsets.UTF_8);

        Map<String, String> messages = FabricMessages.load("en", directory);

        assertEquals("Line\nQuote \"Q\" and \\ slash", messages.get("premium-requested"));
    }

    @Test
    void unsupportedYamlIsRejected() throws Exception {
        Files.writeString(directory.resolve("messages_en.yml"),
                "premium-requested:\n  nested: value\n", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> FabricMessages.load("en", directory));
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        assertEquals(FabricMessages.load("en", directory), FabricMessages.load("unsupported", directory));
    }

    @Test
    void missingChineseOverrideKeyFallsBackToEnglish() throws Exception {
        Files.writeString(directory.resolve("messages_zh.yml"),
                "admin-premium-requested: \"Selected translation\"\n", StandardCharsets.UTF_8);
        Map<String, String> messages = FabricMessages.load("zh", directory);
        assertEquals("Selected translation", messages.get("admin-premium-requested"));
        assertEquals(bundled("en").get("binding-consent-required"),
                messages.get("binding-consent-required"));
        assertEquals(bundled("en").get("same-name-cracked-recorded"),
                messages.get("same-name-cracked-recorded"));
        assertEquals(bundled("en").get("premium-requested"), messages.get("premium-requested"));
    }

    private Map<String, String> bundled(String language) throws Exception {
        try (var input = getClass().getResourceAsStream("/messages_" + language + ".yml")) {
            assertTrue(input != null, "missing bundled language " + language);
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            FlatYamlReader.Result parsed = FlatYamlReader.read(text.lines().toList());
            assertEquals(0, parsed.unsupportedLine());
            Map<String, String> values = new LinkedHashMap<>();
            parsed.values().forEach((key, value) -> values.put(key, (String) value));
            return values;
        }
    }
}
