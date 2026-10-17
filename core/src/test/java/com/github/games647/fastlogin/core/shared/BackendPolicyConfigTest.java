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
