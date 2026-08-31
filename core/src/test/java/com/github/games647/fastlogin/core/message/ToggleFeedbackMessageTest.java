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

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToggleFeedbackMessageTest {

    private static final UUID PROXY_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void roundTripPreservesNameLocaleAndProxyId() {
        ToggleFeedbackMessage original =
                new ToggleFeedbackMessage("Steve", "add-premium-other", PROXY_ID);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        original.writeTo(out);

        ToggleFeedbackMessage decoded = new ToggleFeedbackMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals("Steve", decoded.getPlayerName());
        assertEquals("add-premium-other", decoded.getLocaleId());
        assertEquals(PROXY_ID, decoded.getProxyId());
        assertEquals(ToggleFeedbackMessage.FEEDBACK_CHANNEL, decoded.getChannelName());
    }

    @Test
    void roundTripPreservesErrorLocale() {
        ToggleFeedbackMessage original =
                new ToggleFeedbackMessage("Alex", "database-error", PROXY_ID);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        original.writeTo(out);

        ToggleFeedbackMessage decoded = new ToggleFeedbackMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals("Alex", decoded.getPlayerName());
        assertEquals("database-error", decoded.getLocaleId());
        assertEquals(PROXY_ID, decoded.getProxyId());
    }
}
