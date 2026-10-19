/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ToggleWireTest {

    @Test
    void roundTripsEveryField() throws IOException {
        byte[] payload = serialize(true, "Player_1", false, "id-1,id-2");

        ToggleWire.Toggle parsed = ToggleWire.read(
                new DataInputStream(new ByteArrayInputStream(payload)));

        assertEquals(true, parsed.willEnable());
        assertEquals("Player_1", parsed.playerName());
        assertEquals(false, parsed.isSourceInvoker());
        assertEquals("id-1,id-2", parsed.sourceProxyId());
    }

    @Test
    void legacyPayloadWithoutProxyIdsParsesAsEmptyTrustSet() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeBoolean(false);
            output.writeUTF("Player_1");
            output.writeBoolean(true);
        }

        ToggleWire.Toggle parsed = ToggleWire.read(
                new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));

        assertEquals(false, parsed.willEnable());
        assertEquals("Player_1", parsed.playerName());
        assertEquals(true, parsed.isSourceInvoker());
        assertEquals("", parsed.sourceProxyId());
    }

    @Test
    void wireLayoutIsPinned() throws IOException {
        // boolean, UTF, boolean, UTF — the classic backend and the Fabric
        // module serialize this independently, so the bytes are the contract
        assertArrayEquals(new byte[]{
                1,
                0, 2, 'A', 'B',
                0,
                0, 2, 'i', 'd',
        }, serialize(true, "AB", false, "id"));
    }

    private static byte[] serialize(boolean willEnable, String playerName,
                                    boolean isSourceInvoker, String sourceProxyId) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            ToggleWire.write(output, willEnable, playerName, isSourceInvoker, sourceProxyId);
        }
        return buffer.toByteArray();
    }
}
