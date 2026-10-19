/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.shared.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {

    @Test
    void comparesNumericComponentsAndOptionalPrefix() {
        assertTrue(VersionComparator.isNewer("v0.7.1", "0.7.0"));
        assertTrue(VersionComparator.isNewer("0.8", "0.7.9"));
        assertFalse(VersionComparator.isNewer("0.7.0", "0.7.0"));
        assertFalse(VersionComparator.isNewer("0.6.9", "0.7.0"));
    }

    @Test
    void ignoresPreReleaseSuffixAndTreatsMissingPartsAsZero() {
        assertTrue(VersionComparator.isNewer("1.0.0-beta1", "0.9.9"));
        assertFalse(VersionComparator.isNewer("1.0.0-beta1", "1.0.0"));
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"));
    }

    @Test
    void invalidPartsDoNotCrashTheCheck() {
        assertFalse(VersionComparator.isNewer("v", "0.7.0"));
        assertFalse(VersionComparator.isNewer("0.x.0", "0.7.0"));
    }
}
