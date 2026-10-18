/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AntiBotConfigMigrationTest {

    private static final String WARNING =
            "anti-bot.action is no longer supported; anti-bot always blocks";

    @TempDir
    Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"config.yml", "config-proxy.yml"})
    void legacyKeyWarnsBeforeRefreshAndDisappearsWithoutChangingUserValues(String template) throws Exception {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        for (String action : Arrays.asList("ignore", "block", "invalid", "null")) {
            Path directory = Files.createDirectory(folder.resolve(action));
            Path file = directory.resolve("config.yml");
            String legacyValue = "null".equals(action) ? "null" : "'" + action + "'";
            Files.write(file, ("anti-bot:\n  action: " + legacyValue + "\n"
                    + "  per-ip-connections: 1\n  trusted-ips:\n    - '10.0.0.1'\n")
                    .getBytes(StandardCharsets.UTF_8));
            PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLog()).thenReturn(logger);
            when(plugin.getPluginFolder()).thenReturn(directory);
            doAnswer(invocation -> {
                assertTrue(provider.load(file.toFile()).getSection("anti-bot").getKeys().contains("action"),
                        "The warning must run before the refresh removes the key");
                return null;
            }).when(logger).warn(WARNING);
            FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);
            core.setConfigTemplate(template);

            core.load();

            verify(logger, times(1)).warn(WARNING);
            Configuration refreshed = provider.load(file.toFile());
            assertFalse(refreshed.getSection("anti-bot").getKeys().contains("action"));
            assertFalse(refreshed.contains("anti-bot.action"));
            assertFalse(core.getConfig().contains("anti-bot.action"));
            assertEquals(1, refreshed.getInt("anti-bot.per-ip-connections"));
            assertEquals(Arrays.asList("10.0.0.1"), refreshed.getStringList("anti-bot.trusted-ips"));
            assertEquals(Action.Continue, core.getAntiBotService().onIncomingConnection(
                    new InetSocketAddress("1.2.3.4", 25565), "first"));
            assertEquals(Action.Block, core.getAntiBotService().onIncomingConnection(
                    new InetSocketAddress("1.2.3.4", 25565), "second"));

            FastLoginCore<Object, Object, PlatformPlugin<Object>> restarted = new FastLoginCore<>(plugin);
            restarted.setConfigTemplate(template);
            restarted.load();

            verify(logger, times(1)).warn(WARNING);
            assertFalse(provider.load(file.toFile()).contains("anti-bot.action"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yml", "config-proxy.yml"})
    void freshConfigDoesNotWarnAboutTheRemovedKey(String template) {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getPluginFolder()).thenReturn(folder);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);
        core.setConfigTemplate(template);

        core.load();

        verify(logger, never()).warn(WARNING);
        assertFalse(core.getConfig().contains("anti-bot.action"));
    }
}
