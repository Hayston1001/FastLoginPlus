/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BackendPolicyConfigTest {
    @Test
    void bothTemplatesParseWithUnchangedPolicyDefaults() throws Exception {
        for (String template : new String[]{"/config.yml", "/config-proxy.yml"}) {
            try (InputStream input = getClass().getResourceAsStream(template)) {
                Map<String, Object> settings = new Yaml().load(input);
                assertEquals("en", settings.get("language"));
                for (String key : new String[]{"autoRegister", "autoLogin", "premiumUuid", "nameChangeCheck",
                        "kick-toggle", "auto-register-unknown", "premium-warning"}) {
                    assertEquals(Boolean.TRUE, settings.get(key), template + ": " + key);
                }
                for (String key : new String[]{"secondAttemptCracked", "offline-whitelist", "autoLoginFloodgate",
                        "autoRegisterFloodgate", "allowFloodgateNameConflict"}) {
                    assertEquals(Boolean.FALSE, settings.get(key), template + ": " + key);
                }
                Map<?, ?> antiBot = (Map<?, ?>) settings.get("anti-bot");
                assertEquals(Boolean.TRUE, antiBot.get("enabled"));
                assertEquals("ignore", antiBot.get("action"));
            }
        }
    }

    @Test
    void strictPolicyGateDefaultsOffAndIsOnlyABackendSetting() throws Exception {
        try (InputStream backend = getClass().getResourceAsStream("/config.yml");
             InputStream proxy = getClass().getResourceAsStream("/config-proxy.yml")) {
            Map<String, Object> backendSettings = new Yaml().load(backend);
            Map<String, Object> proxySettings = new Yaml().load(proxy);
            assertEquals(Boolean.FALSE, backendSettings.get("require-declared-proxy-policy"));
            assertFalse(proxySettings.containsKey("require-declared-proxy-policy"));
        }
    }
}
