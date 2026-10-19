/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
