/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

public enum FloodgateState {

    /**
     * Purely Java profile
     */
    FALSE(0, "Java"),

    /**
     * Purely Bedrock profile
     */
    TRUE(1, "Bedrock"),

    /**
     * Bedrock profile is bidirectional associated with the Java Mojang profile.
     */
    LINKED(2, "Linked"),

    /**
     * Data before floodgate database migration. Floodgate state is unknown.
     */
    NOT_MIGRATED(3, "Unknown");

    private final int value;
    private final String readableName;

    FloodgateState(int value) {
        this(value, null);
    }

    FloodgateState(int value, String readableName) {
        this.value = value;
        this.readableName = readableName;
    }

    public int getValue() {
        return value;
    }

    /**
     * @return a human-readable name for this state, or the enum name if none was set
     */
    public String getReadableName() {
        return readableName != null ? readableName : name();
    }

    /**
     * Convert a number to FloodgateState
     * <ol start="0">
     *     <li>False</li>
     *     <li>True</li>
     *     <li>Linked</li>
     *     <li>Not Migrated</li>
     * </ol>
     * @param num the number, most likely loaded from the database
     * @return FloodgateStatus on success, null otherwise
     */
    public static FloodgateState fromInt(int num) {
        // using Enum.values()[i] is expensive as per https://stackoverflow.com/a/8762387/9767089
        switch (num) {
            case 0:
                return FloodgateState.FALSE;
            case 1:
                return FloodgateState.TRUE;
            case 2:
                return FloodgateState.LINKED;
            case 3:
                return FloodgateState.NOT_MIGRATED;
            default:
                return null;
        }
    }
}
