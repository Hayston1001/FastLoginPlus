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
}
