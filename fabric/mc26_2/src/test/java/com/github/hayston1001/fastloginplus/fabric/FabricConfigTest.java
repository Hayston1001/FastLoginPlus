/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.shared.config.ConfigRefresher;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FabricConfigTest {
    private static final String PROXY_ONE = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String PROXY_TWO = "853c80ef-3c37-49fd-aa49-938b674adae6";

    @TempDir
    Path directory;

    private final Logger logger = mock(Logger.class);
    private Object previousSettings;
    private Object previousFailure;

    @BeforeEach
    void isolateStartupSnapshot() throws Exception {
        previousSettings = field("startupSettings").get(null);
        previousFailure = field("startupFailure").get(null);
        field("startupSettings").set(null, null);
        field("startupFailure").set(null, null);
    }

    @AfterEach
    void restoreStartupSnapshot() throws Exception {
        field("startupSettings").set(null, previousSettings);
        field("startupFailure").set(null, previousFailure);
    }

    @Test
    void firstStartCreatesDirectoryAndExactTemplateThenSecondStartKeepsBytes() throws Exception {
        Path config = directory.resolve("new/config");
        assertEquals(Map.of("language", "en", "allowed-proxy-ids", ""), FabricConfig.readFile(config, logger));
        Path yaml = config.resolve("fastloginplus-fabric.yml");
        byte[] first = Files.readAllBytes(yaml);
        try (var input = getClass().getResourceAsStream("/fastloginplus-fabric.yml")) {
            assertArrayEquals(input.readAllBytes(), first);
        }
        assertEquals(0, FlatYamlReader.read(Files.readAllLines(yaml)).unsupportedLine());
        FabricConfig.readFile(config, logger);
        assertArrayEquals(first, Files.readAllBytes(yaml));
        verify(logger).info("Wrote default configuration to {}", yaml);
        verifyNoMoreInteractions(logger);
    }

    @Test
    void migratesOnlyLocalKeysAndPreservesOriginalPropertiesAsBackup() throws Exception {
        Path old = directory.resolve("fastloginplus-fabric.properties");
        String proxies = PROXY_ONE + ", " + PROXY_TWO;
        String legacy = "# Original comment\nlanguage=zh\nallowed-proxy-ids=" + proxies + "\npolicy=ignored\n";
        Files.writeString(old, legacy);
        var values = FabricConfig.readFile(directory, logger);
        assertEquals(Map.of("language", "zh", "allowed-proxy-ids", proxies), values);
        assertFalse(Files.exists(old));
        Path backup = directory.resolve("fastloginplus-fabric.properties.bak");
        assertEquals(legacy, Files.readString(backup));
        Path yaml = yaml();
        byte[] first = Files.readAllBytes(yaml);
        assertEquals(values, FlatYamlReader.read(Files.readAllLines(yaml)).values());
        verify(logger).info("Migrated {} to {}; kept the original as {}", old, yaml, backup);
        // Even a recreated legacy file must not override the migrated YAML.
        Files.writeString(old, "language=en\nallowed-proxy-ids=untrusted\n");
        assertEquals(values, FabricConfig.readFile(directory, logger));
        assertArrayEquals(first, Files.readAllBytes(yaml));
        assertEquals(legacy, Files.readString(backup));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void existingYamlWinsWithoutReadingOrRenamingLegacyProperties() throws Exception {
        Files.writeString(yaml(), "language: zh\nallowed-proxy-ids: '" + PROXY_ONE + "'\n");
        Path old = directory.resolve("fastloginplus-fabric.properties");
        Files.writeString(old, "language=en\nallowed-proxy-ids=wrong\n");
        assertEquals(PROXY_ONE, FabricConfig.readFile(directory, logger).get("allowed-proxy-ids"));
        assertTrue(Files.exists(old));
        assertFalse(Files.exists(directory.resolve("fastloginplus-fabric.properties.bak")));
        verifyNoInteractions(logger);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[first, 'second']", "\n  - first\n  - second"})
    void listInputIsNormalizedBeforeRefreshAndSecondStartKeepsBytes(String list) throws Exception {
        Files.writeString(yaml(), "# Old comments\nlanguage: zh\nallowed-proxy-ids: " + list + "\nobsolete: remove\n");
        assertEquals("first,second", FabricConfig.readFile(directory, logger).get("allowed-proxy-ids"));
        String output = Files.readString(yaml());
        assertTrue(output.contains("allowed-proxy-ids: 'first,second'\n"));
        assertTrue(output.contains("# Configuration changes require a backend restart."));
        assertFalse(output.contains("Old comments"));
        assertFalse(output.contains("obsolete"));
        byte[] first = Files.readAllBytes(yaml());
        assertEquals("first,second", FabricConfig.readFile(directory, logger).get("allowed-proxy-ids"));
        assertArrayEquals(first, Files.readAllBytes(yaml()));
        verifyNoInteractions(logger);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "", "''", "'  '"})
    void emptyTrustValuesStillTriggerTheExistingWarning(String value) throws Exception {
        Files.writeString(yaml(), "allowed-proxy-ids: " + value + "\n");
        String raw = (String) FabricConfig.readFile(directory, logger).get("allowed-proxy-ids");
        FabricConfig.warnIfUntrusted(new FabricConfig.StartupSettings(raw), logger);
        verify(logger).warn("fastloginplus-fabric.yml has an empty allowed-proxy-ids; the proxy "
                + "will refuse the upgrade consent message and /flp premium can never complete. "
                + "Fill in the proxy's UUID (its proxyId.txt).");
        verifyNoMoreInteractions(logger);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  nested: wrong", "language: &anchor en", "language: !!str en"})
    void unsupportedShapeWarnsOnceKeepsPrefixValuesAndNeverChangesFile(String unsupported) throws Exception {
        String original = "# Kept byte for byte\r\nlanguage: zh\r\nallowed-proxy-ids: [first, second]\r\n"
                + unsupported + "\r\nlanguage: en\r\n";
        Files.writeString(yaml(), original);
        byte[] before = Files.readAllBytes(yaml());
        var values = FabricConfig.readFile(directory, logger);
        assertEquals("zh", values.get("language"));
        assertEquals("first,second", values.get("allowed-proxy-ids"));
        assertArrayEquals(before, Files.readAllBytes(yaml()));
        verify(logger).warn("Unsupported YAML in {} at line {}; keeping earlier values and skipping template refresh",
                yaml(), 4);
        verifyNoMoreInteractions(logger);
    }

    @Test
    void actualStartupUsesParsedPrefixAndFreezesLanguageAndTrustUntilRestart() throws Exception {
        Files.writeString(yaml(), "language: zh\nallowed-proxy-ids: [" + PROXY_ONE + "]\n  nested: bad\n");
        byte[] before = Files.readAllBytes(yaml());
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(directory);
        try (var fabric = mockStatic(FabricLoader.class); var messages = mockStatic(FabricMessages.class)) {
            fabric.when(FabricLoader::getInstance).thenReturn(loader);
            var snapshot = FabricConfig.loadAtStartup();
            assertEquals(PROXY_ONE, snapshot.allowedProxyIds());
            assertArrayEquals(before, Files.readAllBytes(yaml()));
            Files.writeString(yaml(), "language: en\nallowed-proxy-ids: different\n");
            assertSame(snapshot, FabricConfig.loadAtStartup());
            assertSame(snapshot, FabricConfig.startup());
            messages.verify(() -> FabricMessages.load("zh"));
            messages.verifyNoMoreInteractions();
            verify(loader).getConfigDir();
        }
    }

    @Test
    void actualStartupSuppliesMissingLanguageAndTrustDefaults() throws Exception {
        Files.writeString(yaml(), "# Settings not specified\n");
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(directory);
        try (var fabric = mockStatic(FabricLoader.class); var messages = mockStatic(FabricMessages.class)) {
            fabric.when(FabricLoader::getInstance).thenReturn(loader);
            assertEquals("", FabricConfig.loadAtStartup().allowedProxyIds());
            messages.verify(() -> FabricMessages.load("en"));
            assertEquals(Map.of("language", "en", "allowed-proxy-ids", ""),
                    FlatYamlReader.read(Files.readAllLines(yaml())).values());
        }
    }

    @Test
    void startupFailureIsFrozenInsteadOfSilentlyLoadingDefaultsOnRetry() throws Exception {
        Path notDirectory = directory.resolve("not-directory");
        Files.writeString(notDirectory, "occupied");
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(notDirectory);
        try (var fabric = mockStatic(FabricLoader.class); var messages = mockStatic(FabricMessages.class)) {
            fabric.when(FabricLoader::getInstance).thenReturn(loader);
            var failure = assertThrows(IllegalStateException.class, FabricConfig::loadAtStartup);
            assertTrue(failure.getMessage().contains("fastloginplus-fabric.yml"));
            assertSame(failure, assertThrows(IllegalStateException.class, FabricConfig::loadAtStartup));
            assertSame(failure, assertThrows(IllegalStateException.class, FabricConfig::startup));
            verify(loader).getConfigDir();
            messages.verifyNoInteractions();
        }
    }

    @Test
    void backupCollisionNeverOverwritesExistingBackupOrDeletesOriginal() throws Exception {
        Path old = directory.resolve("fastloginplus-fabric.properties");
        Files.writeString(old, "language=zh\nallowed-proxy-ids=" + PROXY_ONE + "\n");
        Path backup = directory.resolve("fastloginplus-fabric.properties.bak");
        Files.writeString(backup, "previous backup");
        byte[] original = Files.readAllBytes(old);
        assertThrows(IllegalStateException.class, () -> FabricConfig.readFile(directory, logger));
        assertEquals("previous backup", Files.readString(backup));
        assertArrayEquals(original, Files.readAllBytes(old));
        assertEquals(PROXY_ONE, FlatYamlReader.read(Files.readAllLines(yaml())).values().get("allowed-proxy-ids"));
        verifyNoInteractions(logger);
    }

    @Test
    void unreadableYamlLeavesLegacyFileUntouchedAndDoesNotCreateBackup() throws Exception {
        Path old = directory.resolve("fastloginplus-fabric.properties");
        Files.writeString(old, "language=zh\nallowed-proxy-ids=" + PROXY_ONE + "\n", StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(old);
        // An existing directory at the YAML path cannot be read or replaced as a config file.
        Files.createDirectory(yaml());
        assertThrows(IllegalStateException.class, () -> FabricConfig.readFile(directory, logger));
        assertArrayEquals(original, Files.readAllBytes(old));
        assertFalse(Files.exists(directory.resolve("fastloginplus-fabric.properties.bak")));
    }

    @Test
    void migrationWriteFailurePreservesOriginalAndNeverRenamesIt() throws Exception {
        Path old = directory.resolve("fastloginplus-fabric.properties");
        Files.writeString(old, "language=zh\nallowed-proxy-ids=" + PROXY_ONE + "\n");
        byte[] original = Files.readAllBytes(old);
        try (var refresher = mockStatic(ConfigRefresher.class)) {
            refresher.when(() -> ConfigRefresher.refresh(any(), any(), any()))
                    .thenThrow(new IOException("Injected write failure"));
            var failure = assertThrows(IllegalStateException.class, () -> FabricConfig.readFile(directory, logger));
            assertEquals("Injected write failure", failure.getCause().getMessage());
            assertArrayEquals(original, Files.readAllBytes(old));
            assertFalse(Files.exists(yaml()));
            assertFalse(Files.exists(directory.resolve("fastloginplus-fabric.properties.bak")));
            verifyNoInteractions(logger);
        }
    }

    @Test
    void refreshWriteFailureDoesNotReplaceTheExistingYamlWithDefaults() throws Exception {
        String original = "# Keep on failure\nlanguage: zh\nallowed-proxy-ids: '" + PROXY_ONE + "'\n";
        Files.writeString(yaml(), original);
        try (var refresher = mockStatic(ConfigRefresher.class)) {
            refresher.when(() -> ConfigRefresher.refresh(any(), any(), any()))
                    .thenThrow(new IOException("Injected refresh failure"));
            assertThrows(IllegalStateException.class, () -> FabricConfig.readFile(directory, logger));
            assertEquals(original, Files.readString(yaml()));
            verifyNoInteractions(logger);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"first\\nsecond", "first\\u0000second", "\\uD800"})
    void unsupportedLegacyScalarNeverCreatesBrokenYamlOrRenamesOriginal(String value) throws Exception {
        Path old = directory.resolve("fastloginplus-fabric.properties");
        Files.writeString(old, "language=zh\nallowed-proxy-ids=" + value + "\n");
        byte[] original = Files.readAllBytes(old);
        assertThrows(IllegalStateException.class, () -> FabricConfig.readFile(directory, logger));
        assertArrayEquals(original, Files.readAllBytes(old));
        assertFalse(Files.exists(yaml()));
        assertFalse(Files.exists(directory.resolve("fastloginplus-fabric.properties.bak")));
        verifyNoInteractions(logger);
    }

    private Path yaml() {
        return directory.resolve("fastloginplus-fabric.yml");
    }

    private static Field field(String name) throws Exception {
        Field field = FabricConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
