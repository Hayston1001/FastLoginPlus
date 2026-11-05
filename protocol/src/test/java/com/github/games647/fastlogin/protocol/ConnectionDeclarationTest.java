/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionDeclarationTest {
    @Test
    void roundTripPreservesEveryDecisionAndConnectionBinding() throws Exception {
        UUID uuid = UUID.randomUUID();
        for (IdentityClaim.Decision decision : IdentityClaim.Decision.values()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new ConnectionDeclaration(decision, uuid).write(new DataOutputStream(bytes));
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(ConnectionDeclaration.MARKER, input.readUnsignedByte());
            ConnectionDeclaration decoded = ConnectionDeclaration.readPayload(input);
            assertEquals(decision, decoded.getDecision());
            assertEquals(uuid, decoded.getForwardedUuid());
        }
    }

    @Test
    void unsupportedVersionAndDecisionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionDeclaration.readPayload(
                new DataInputStream(new ByteArrayInputStream(new byte[]{2, 1}))));
        assertThrows(IllegalArgumentException.class, () -> ConnectionDeclaration.readPayload(
                new DataInputStream(new ByteArrayInputStream(new byte[]{1, 3}))));
    }

    @Test
    void bedrockFlagRoundTripsAndAShortFrameIsRejected() throws Exception {
        UUID uuid = UUID.randomUUID();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ConnectionDeclaration(IdentityClaim.Decision.UNDECIDED, uuid, true)
                .write(new DataOutputStream(bytes));
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(ConnectionDeclaration.MARKER, input.readUnsignedByte());
        assertTrue(ConnectionDeclaration.readPayload(input).isBedrock());

        // a frame that stops before the flag is truncated, never "not Bedrock"
        byte[] full = bytes.toByteArray();
        DataInputStream truncated = new DataInputStream(
                new ByteArrayInputStream(java.util.Arrays.copyOf(full, full.length - 1)));
        assertEquals(ConnectionDeclaration.MARKER, truncated.readUnsignedByte());
        assertThrows(java.io.EOFException.class, () -> ConnectionDeclaration.readPayload(truncated));
    }
}
