/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.messaging.Messenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BungeeManagerDetectionTest {
    private static final String SPIGOT = "org.spigotmc.SpigotConfig";
    private final UUID proxyId = UUID.randomUUID();
    @TempDir
    Path tempDir;
    private FastLoginBukkit plugin;
    private Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FastLoginBukkit.class);
        logger = mock(Logger.class);
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getName()).thenReturn("FastLoginPlus");
        when(plugin.getPluginFolder()).thenReturn(tempDir);
        Files.write(tempDir.resolve("allowed-proxies.txt"),
                Collections.singleton(proxyId.toString()), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @CsvSource({"false, false, false, false", "true, false, true, false",
            "false, true, true, true", "true, true, true, false"})
    void forwardingDetectionIsCachedAndBothEnabledRemainsStrict(boolean legacy, boolean modern,
                                                                boolean enabled, boolean signed) throws Exception {
        BungeeManager manager = manager(legacy, modern);
        initialize(manager);

        for (int login = 0; login < 10; login++) {
            assertEquals(enabled, manager.isEnabled());
            assertEquals(signed, manager.isSignedForwarding());
        }
        manager.initialize();
        verify(manager, times(1)).isProxySupported(SPIGOT, "bungee");
        verify(manager, times(1)).isVelocityEnabled();
    }

    @Test
    void missingPaperClassesOnSpigotAreAnOrdinaryNegativeResult() throws Exception {
        BungeeManager manager = spy(new BungeeManager(plugin));
        doReturn(true).when(manager).isProxySupported(SPIGOT, "bungee");
        initialize(manager);

        assertTrue(manager.isEnabled());
        assertFalse(manager.isSignedForwarding());
        verify(manager, times(1)).isVelocityEnabled();
        verify(logger, never()).warn(anyString(), any(Throwable.class));
        verify(logger, never()).warn(anyString(), any(Object.class));
        verify(logger, never()).warn(anyString());
    }

    @Test
    void unexpectedReflectionFailuresProduceOnlyOneDetectionWarning() throws Exception {
        BungeeManager manager = spy(new BungeeManager(plugin));
        doThrow(new NoSuchFieldException("bungee")).when(manager).isProxySupported(SPIGOT, "bungee");
        doThrow(new NoSuchMethodException("get")).when(manager).isVelocityEnabled();
        initialize(manager);

        assertFalse(manager.isEnabled());
        for (int login = 0; login < 10; login++) {
            assertFalse(manager.isSignedForwarding());
        }
        manager.initialize();
        verify(logger, times(1)).warn(contains("Cannot check the proxy forwarding setup"), any(Throwable.class));
        verify(manager, times(1)).isVelocityEnabled();
    }

    @Test
    void unknownLegacyConfigurationCannotRelaxModernForwardingValidation() throws Exception {
        BungeeManager manager = manager(false, true);
        doThrow(new IllegalAccessException("bungee")).when(manager).isProxySupported(SPIGOT, "bungee");
        initialize(manager);

        assertTrue(manager.isEnabled());
        assertFalse(manager.isSignedForwarding());
        verify(logger).warn(contains("Cannot check the proxy forwarding setup"), any(Throwable.class));
    }

    @Test
    void allowlistStaysAtItsStartupSnapshotAndLogsOnlyItsCount() throws Exception {
        BungeeManager manager = manager(true, false);
        initialize(manager);
        UUID replacement = UUID.randomUUID();
        Files.write(tempDir.resolve("allowed-proxies.txt"),
                Collections.singleton(replacement.toString()), StandardCharsets.UTF_8);

        manager.initialize();

        assertEquals(Collections.singleton(proxyId), manager.getAllowedProxyIds());
        assertFalse(manager.isProxyAllowed(replacement));
        verify(logger, times(1)).info("Loaded {} proxy IDs from allowed-proxies.txt. "
                + "Changing this file requires a backend restart.", 1);
        assertTrue(mockingDetails(logger).getInvocations().stream()
                .flatMap(invocation -> java.util.Arrays.stream(invocation.getArguments()))
                .noneMatch(argument -> String.valueOf(argument).contains(proxyId.toString())));
    }

    private BungeeManager manager(boolean legacy, boolean modern) throws Exception {
        BungeeManager manager = spy(new BungeeManager(plugin));
        doReturn(legacy).when(manager).isProxySupported(SPIGOT, "bungee");
        doReturn(modern).when(manager).isVelocityEnabled();
        return manager;
    }

    private void initialize(BungeeManager manager) {
        Server server = mock(Server.class);
        when(server.getMessenger()).thenReturn(mock(Messenger.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            manager.initialize();
        }
    }
}
