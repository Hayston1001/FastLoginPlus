/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Type adapter for converting UUIDs from Mojang's string representation into a Java version.
 * <p>
 * It uses a faster UUID implementation for toString() (if < Java 9) outputs and dashed UUIDs parsing.
 */
public class UUIDAdapter extends TypeAdapter<UUID> {

    private static final String OFFLINE_PREFIX = "OfflinePlayer:";

    @Override
    public void write(JsonWriter out, UUID value) throws IOException {
        out.value(toMojangId(value));
    }

    @Override
    public UUID read(JsonReader in) throws IOException {
        return parseId(in.nextString());
    }

    /**
     * Converts a Mojang UUID (UUID without dashes) into a Java representation of the UUID.
     * @param withoutDashes uuid without dashes
     * @return parsed UUID
     */
    public static UUID parseId(CharSequence withoutDashes) {
        return FastUUID.parseUUIDUUndashed(withoutDashes);
    }

    /**
     * Parses the string representation of UUIDs with dashes. This simulates the standard Java behavior, but with
     * performance improvements.
     *
     * @param uuidDashed uuid with dashes
     * @return parsed UUID
     */
    public static UUID parseDashedId(CharSequence uuidDashed) {
        return FastUUID.parseUUIDDashed(uuidDashed);
    }

    /**
     * Converts the UUID to a String representation without dashes.
     * @param uniqueId UUID that should be converted
     * @return UUID without dashes
     */
    public static String toMojangId(UUID uniqueId) {
        return FastUUID.toStringUndashed(uniqueId);
    }

    /**
     * Converts the UUID to a String representation with dashes.
     * @param uniqueId UUID that should be converted
     * @return UUID with dashes
     */
    public static String toString(UUID uniqueId) {
        return FastUUID.toStringDashed(uniqueId);
    }

    /**
     * Generates the offline/cracked UUID based on the player name. This method is equal to the server side
     * implementation if the server is in offline mode.
     * @param playerName case-sensitive player name
     * @return offline/cracked UUID
     */
    public static UUID generateOfflineId(String playerName) {
        Objects.requireNonNull(playerName);
        return UUID.nameUUIDFromBytes((OFFLINE_PREFIX + playerName).getBytes(StandardCharsets.UTF_8));
    }
}
