/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.util.Optional;
import java.util.regex.Pattern;

/** Pure compatibility decisions, independent of Fabric Loader and the mod's origin. */
final class EasyAuthVersionPolicy {
    private static final Pattern SUPPORTED = Pattern.compile(
            "3\\.4\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

    private EasyAuthVersionPolicy() {
    }

    static boolean supports(String version) {
        return version != null && SUPPORTED.matcher(version).matches();
    }

    static Optional<String> fingerprintWarning(String expected, String actual) {
        if (expected.equals(actual)) {
            return Optional.empty();
        }
        return Optional.of("EasyAuth JAR SHA-256 expected=" + expected + " actual="
                + (actual == null ? "unavailable" : actual)
                + "; continuing because the 3.4.x API members were verified."
                + " Member checks do not guarantee equivalent account or login behavior.");
    }
}
