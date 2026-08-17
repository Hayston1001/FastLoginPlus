/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.core.shared;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRefresherTest {

    private static final String FULL_TEMPLATE = "config.yml";
    private static final String PROXY_TEMPLATE = "config-proxy.yml";

    private final ConfigurationProvider provider =
            ConfigurationProvider.getProvider(YamlConfiguration.class);

    @TempDir
    Path tempDir;

    @Test
    void backendConfigMigratedToProxyTemplateDropsBackendOnlyKeysAndPreservesValues()
            throws IOException {
        // An admin's existing backend config, migrated to a proxy: the
        // backend-only keys must disappear while every user value survives.
        Path config = copyResource(FULL_TEMPLATE);
        Configuration userConfig = load(config);
        userConfig.set("language", "zh");
        userConfig.set("autoRegister", false);
        userConfig.set("verifyClientKeys", true);
        userConfig.set("useProxyAgnosticResolver", false);
        userConfig.set("ServerRSAPublicKeyFile", "/my/custom/key.pem");
        userConfig.set("anti-bot.trusted-ips", Arrays.asList("127.0.0.1", "10.0.0.5"));
        userConfig.set("proxies", Arrays.asList("proxy1.example:8080"));
        provider.save(userConfig, config.toFile());

        ConfigRefresher.refresh(getClass().getClassLoader(), config, load(config), PROXY_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertFalse(output.contains("verifyClientKeys:"));
        assertFalse(output.contains("respectIpLimit:"));
        assertFalse(output.contains("useProxyAgnosticResolver:"));
        // Comments come from the proxy template
        assertTrue(output.contains("FastLoginPlus Configuration (BungeeCord / Velocity proxy)"));

        Configuration refreshed = load(config);
        assertEquals("zh", refreshed.getString("language"));
        assertFalse(refreshed.getBoolean("autoRegister"));
        assertFalse(refreshed.contains("verifyClientKeys"));
        // Scalar key written without a template value keeps the user's value
        assertEquals("/my/custom/key.pem", refreshed.getString("ServerRSAPublicKeyFile"));
        assertEquals(Arrays.asList("127.0.0.1", "10.0.0.5"),
                refreshed.getStringList("anti-bot.trusted-ips"));
        assertEquals(Arrays.asList("proxy1.example:8080"), refreshed.getStringList("proxies"));
    }

    @Test
    void proxyConfigMigratedToBackendTemplateRestoresMissingKeys() throws IOException {
        // The reverse copy: keys absent from the trimmed proxy config come
        // back with the backend template defaults.
        Path config = copyResource(PROXY_TEMPLATE);

        ConfigRefresher.refresh(getClass().getClassLoader(), config, load(config), FULL_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertTrue(output.contains("verifyClientKeys: false"));
        assertTrue(output.contains("respectIpLimit: false"));
        assertTrue(output.contains("useProxyAgnosticResolver: true"));
        assertTrue(output.contains("FastLoginPlus Configuration (Bukkit / Folia)"));
    }

    @Test
    void freshProxyConfigRefreshIsStableAndStaysTrimmed() throws IOException {
        // A fresh proxy install must stay trimmed across refresh cycles.
        Path config = copyResource(PROXY_TEMPLATE);

        ConfigRefresher.refresh(getClass().getClassLoader(), config, load(config), PROXY_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertFalse(output.contains("verifyClientKeys:"));
        assertFalse(output.contains("respectIpLimit:"));
        assertFalse(output.contains("useProxyAgnosticResolver:"));

        Configuration refreshed = load(config);
        assertEquals("en", refreshed.getString("language"));
        assertTrue(refreshed.getBoolean("autoLogin"));
        assertTrue(refreshed.getBoolean("premiumUuid"));
    }

    private Path copyResource(String resource) throws IOException {
        Path target = tempDir.resolve("config.yml");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            Files.copy(Objects.requireNonNull(is, "missing resource " + resource), target);
        }
        return target;
    }

    private Configuration load(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return provider.load(reader);
        }
    }
}
