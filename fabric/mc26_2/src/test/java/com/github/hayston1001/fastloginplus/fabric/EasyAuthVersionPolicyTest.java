/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class EasyAuthVersionPolicyTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"3.4.4", "3.4.5", "3.4.4+build.7", "3.4.0", "3.4.12-custom+build.7"})
    void acceptsTheSameMinorWithAnOptionalBuildSuffix(String version) {
        assertTrue(EasyAuthVersionPolicy.supports(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.5.0", "3.3.9", "3.4", "garbage", "乱码", "3.4.x", "3.4.4+", "3.4.4 ",
            "3.4.4.1", "3.4.-1", "3.4.04"})
    void refusesOtherMinorsAndMalformedVersions(String version) {
        assertFalse(EasyAuthVersionPolicy.supports(version));
    }

    @Test
    void refusesMissingVersion() {
        assertFalse(EasyAuthVersionPolicy.supports(null));
    }

    @Test
    void matchingFingerprintContinuesWithoutWarning() {
        assertTrue(assertDoesNotThrow(() -> EasyAuthVersionPolicy.fingerprintWarning("expected", "expected"))
                .isEmpty());
    }

    @Test
    void differentFingerprintContinuesWithExpectedAndActualWarning() {
        String warning = assertDoesNotThrow(() -> EasyAuthVersionPolicy.fingerprintWarning("expected", "actual"))
                .orElseThrow();
        assertTrue(warning.contains("expected=expected actual=actual"));
        assertTrue(warning.contains("continuing because the 3.4.x API members were verified"));
        assertTrue(warning.contains("do not guarantee"));
    }

    @Test
    void unavailableFingerprintContinuesWithOneWarning() {
        String warning = assertDoesNotThrow(() -> EasyAuthVersionPolicy.fingerprintWarning("expected", null))
                .orElseThrow();
        assertTrue(warning.contains("expected=expected actual=unavailable"));
        assertTrue(warning.contains("continuing"));
    }

    @Test
    void standaloneSelfBuiltJarIsHashedAndWarnedOnceWithoutRejecting() throws Exception {
        byte[] bytes = {1, 2, 3};
        Path jar = Files.write(directory.resolve("self-built.jar"), bytes);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String warning = warnFor(paths(jar));
        assertTrue(warning.contains("actual=" + actual));
    }

    @Test
    void developmentMissingMultipleAndNonJarPathsAreUnavailableAndNeverRejected() throws Exception {
        Path notJar = Files.writeString(directory.resolve("classes.bin"), "not a jar");
        Path jar = Files.writeString(directory.resolve("mod.jar"), "jar bytes");
        for (ModContainer mod : List.of(paths(directory), paths(directory.resolve("missing.jar")),
                paths(jar, jar), paths(), paths(notJar))) {
            assertTrue(warnFor(mod).contains("actual=unavailable"));
        }
    }

    @Test
    void originLookupFailureIsUnavailableAndNeverRejected() {
        ModContainer mod = mock(ModContainer.class);
        when(mod.getOrigin()).thenThrow(new IllegalStateException("development origin unavailable"));
        assertTrue(warnFor(mod).contains("actual=unavailable"));
    }

    private ModContainer paths(Path... paths) {
        ModContainer mod = mock(ModContainer.class);
        ModOrigin origin = mock(ModOrigin.class);
        when(mod.getOrigin()).thenReturn(origin);
        when(origin.getKind()).thenReturn(ModOrigin.Kind.PATH);
        when(origin.getPaths()).thenReturn(List.of(paths));
        return mod;
    }

    private String warnFor(ModContainer mod) {
        Logger logger = mock(Logger.class);
        assertDoesNotThrow(() -> EasyAuth344Bridge.warnReleaseJar(mod, logger));
        ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(warning.capture());
        verifyNoMoreInteractions(logger);
        return warning.getValue();
    }
}
