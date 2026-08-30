/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.core.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.5.0/F054: the backend -> proxy channels (ch-st, del-st, succ) carry the
 * sending backend's echoed proxy allowlist as a trailing optional wire field.
 */
class ProxyAuthenticatedMessageTest {

    private static final String SINGLE_ID = "0f6f2f66-bc5d-4d9a-a1f1-3b0a2c1c6b31";
    private static final String OTHER_ID = "1f6f2f66-bc5d-4d9a-a1f1-3b0a2c1c6b32";

    @Test
    void changeMessageRoundTripWithSourceProxyId() {
        ChangePremiumMessage message = new ChangePremiumMessage("Steve", true, false);
        message.setSourceProxyId(SINGLE_ID);

        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        message.writeTo(output);
        message.readFrom(ByteStreams.newDataInput(output.toByteArray()));

        assertTrue(message.shouldEnable());
        assertEquals("Steve", message.getPlayerName());
        assertFalse(message.isSourceInvoker());
        assertEquals(SINGLE_ID, message.getSourceProxyId());
    }

    @Test
    void deleteMessageRoundTripWithSourceProxyId() {
        DeletePremiumMessage message = new DeletePremiumMessage("Alex", true);
        message.setSourceProxyId(SINGLE_ID + "," + OTHER_ID);

        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        message.writeTo(output);
        message.readFrom(ByteStreams.newDataInput(output.toByteArray()));

        assertEquals("Alex", message.getPlayerName());
        assertTrue(message.isSourceInvoker());
        assertEquals(SINGLE_ID + "," + OTHER_ID, message.getSourceProxyId());
    }

    @Test
    void successMessageRoundTripWithSourceProxyId() {
        SuccessMessage message = new SuccessMessage();
        message.setSourceProxyId(SINGLE_ID);

        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        message.writeTo(output);
        message.readFrom(ByteStreams.newDataInput(output.toByteArray()));

        assertEquals(SINGLE_ID, message.getSourceProxyId());
    }

    @Test
    void unsetSourceProxyIdDefaultsToEmptyString() {
        ChangePremiumMessage message = new ChangePremiumMessage("Steve", false, true);
        DeletePremiumMessage deleteMessage = new DeletePremiumMessage("Alex", false);

        assertEquals("", message.getSourceProxyId());
        assertEquals("", deleteMessage.getSourceProxyId());
        assertEquals("", new SuccessMessage().getSourceProxyId());
    }

    @Test
    void legacyChangePayloadWithoutTrailingFieldFallsBackToEmpty() {
        // payload as written by an older backend: no trailing sourceProxyId
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeBoolean(true);
        output.writeUTF("Steve");
        output.writeBoolean(false);

        ChangePremiumMessage message = new ChangePremiumMessage();
        message.readFrom(ByteStreams.newDataInput(output.toByteArray()));

        assertTrue(message.shouldEnable());
        assertEquals("Steve", message.getPlayerName());
        assertEquals("", message.getSourceProxyId());
    }

    @Test
    void legacyDeletePayloadWithoutTrailingFieldFallsBackToEmpty() {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("Alex");
        output.writeBoolean(true);

        DeletePremiumMessage message = new DeletePremiumMessage();
        message.readFrom(ByteStreams.newDataInput(output.toByteArray()));

        assertEquals("Alex", message.getPlayerName());
        assertTrue(message.isSourceInvoker());
        assertEquals("", message.getSourceProxyId());
    }

    @Test
    void legacySuccessPayloadWithoutTrailingFieldFallsBackToEmpty() {
        // older backends sent a completely empty payload on the succ channel
        ByteArrayDataInput input = ByteStreams.newDataInput(new byte[0]);

        SuccessMessage message = new SuccessMessage();
        message.readFrom(input);

        assertEquals("", message.getSourceProxyId());
    }

    @Test
    void wireFormatAlwaysAppendsTrailingField() {
        // a fresh message with no stamp still writes the (empty) trailing field
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        new SuccessMessage().writeTo(output);

        byte[] data = output.toByteArray();
        assertEquals(2, data.length); // writeUTF("") = 2-byte length prefix

        SuccessMessage message = new SuccessMessage();
        message.readFrom(ByteStreams.newDataInput(data));
        assertEquals("", message.getSourceProxyId());
    }
}
