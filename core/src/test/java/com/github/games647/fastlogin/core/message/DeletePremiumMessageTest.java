/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletePremiumMessageTest {

    @Test
    void roundTripPreservesNameAndSourceInvoker() {
        DeletePremiumMessage original = new DeletePremiumMessage("Steve", true);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        original.writeTo(out);

        DeletePremiumMessage decoded = new DeletePremiumMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals("Steve", decoded.getPlayerName());
        assertTrue(decoded.isSourceInvoker());
        assertEquals(DeletePremiumMessage.DELETE_CHANNEL, decoded.getChannelName());
    }

    @Test
    void consoleRelayCarriesFalseInvokerFlag() {
        DeletePremiumMessage original = new DeletePremiumMessage("Alex", false);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        original.writeTo(out);

        DeletePremiumMessage decoded = new DeletePremiumMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals("Alex", decoded.getPlayerName());
        assertFalse(decoded.isSourceInvoker());
    }

    @Test
    void legacyFormatWithoutInvokerFlagDefaultsToFalse() {
        // legacy payload: player name only, no trailing boolean
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Steve");

        DeletePremiumMessage decoded = new DeletePremiumMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals("Steve", decoded.getPlayerName());
        assertFalse(decoded.isSourceInvoker());
    }
}
