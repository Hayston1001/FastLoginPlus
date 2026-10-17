/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.util.UUID;

/** Recognizes unsupported identities, never authenticates a connection. */
final class BedrockBoundary {
    private BedrockBoundary() {
    }

    static boolean isBedrock(String name, UUID uuid) {
        return (name != null && name.startsWith("."))
                || (uuid != null && uuid.getMostSignificantBits() == 0
                && uuid.getLeastSignificantBits() >>> 48 == 9
                && ((uuid.getLeastSignificantBits() >>> 40) & 0xff) == 1);
    }
}
