/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

public enum PremiumStatus {

    PREMIUM("Premium"),

    CRACKED("Cracked"),

    UNKNOWN("Unknown");

    private final String readableName;

    PremiumStatus(String readableName) {
        this.readableName = readableName;
    }

    public String getReadableName() {
        return readableName;
    }

    /**
     * @return true if this status represents a premium (paid) account
     */
    public boolean isPremium() {
        return this == PREMIUM;
    }
}
