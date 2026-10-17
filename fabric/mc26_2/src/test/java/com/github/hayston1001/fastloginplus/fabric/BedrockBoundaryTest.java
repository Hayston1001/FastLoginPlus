/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {"00000000-0000-0000-0009-01eed05d107b", "00000000-0000-0000-0009-010000000123"})
    void floodgateUuidIsDetectedEvenWithoutTheDefaultNamePrefix(String value) {
        assertTrue(BedrockBoundary.isBedrock("Name", UUID.fromString(value)));
    }

    @Test
    void mojangAndOfflineJavaUuidsDoNotMatch() {
        UUID mojang = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Name".getBytes(StandardCharsets.UTF_8));
        assertTrue(mojang.version() == 4 && offline.version() == 3);
        assertFalse(BedrockBoundary.isBedrock("Name", mojang));
        assertFalse(BedrockBoundary.isBedrock("Name", offline));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000-0000-0001-0009-01eed05d107b", "00000000-0000-0000-0008-01eed05d107b",
            "00000000-0000-0000-0009-00eed05d107b", "00000000-0000-0000-0009-02eed05d107b"})
    void everyUuidPrefixComponentMustMatch(String value) {
        assertFalse(BedrockBoundary.isBedrock("Name", UUID.fromString(value)));
    }

    @Test
    void defaultFloodgateNamePrefixIsAnIndependentSignal() {
        UUID mojang = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertTrue(BedrockBoundary.isBedrock(".Name", mojang));
        assertTrue(BedrockBoundary.isBedrock(".Name", null));
        assertFalse(BedrockBoundary.isBedrock("Name", mojang));
        assertFalse(BedrockBoundary.isBedrock(null, null));
    }
}
