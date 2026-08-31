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
package com.github.games647.fastlogin.bungee.listener;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.5.0/F054: authentication decision for backend -> proxy plugin messages.
 * The message is trusted only when this proxy's own ID is part of the echoed
 * proxy allowlist; everything else fails closed.
 */
class PluginMessageListenerAcceptsTest {

    private final UUID ownId = UUID.fromString("0f6f2f66-bc5d-4d9a-a1f1-3b0a2c1c6b31");
    private final UUID foreignId = UUID.fromString("1f6f2f66-bc5d-4d9a-a1f1-3b0a2c1c6b32");

    @Test
    void acceptsWhenOwnIdIsTheSingleEchoedEntry() {
        assertTrue(PluginMessageListener.accepts(ownId.toString(), ownId));
    }

    @Test
    void acceptsWhenOwnIdIsPartOfCommaJoinedSet() {
        String echoed = foreignId + "," + ownId;
        assertTrue(PluginMessageListener.accepts(echoed, ownId));
    }

    @Test
    void rejectsForeignId() {
        assertFalse(PluginMessageListener.accepts(foreignId.toString(), ownId));
    }

    @Test
    void rejectsForeignOnlySet() {
        String echoed = foreignId + "," + UUID.randomUUID();
        assertFalse(PluginMessageListener.accepts(echoed, ownId));
    }

    @Test
    void rejectsEmptyEchoedSet() {
        // empty set = backend has no trusted proxy (0.5.0/F015 semantics)
        assertFalse(PluginMessageListener.accepts("", ownId));
    }

    @Test
    void rejectsLegacyPayloadWithoutEchoField() {
        // legacy backend payloads degrade to the empty string in readFrom
        assertFalse(PluginMessageListener.accepts("", ownId));
    }

    @Test
    void rejectsUnparsableEchoedSet() {
        assertFalse(PluginMessageListener.accepts("not-a-uuid", ownId));
    }

    @Test
    void skipsMalformedEntriesButAcceptsValidOnes() {
        String echoed = "not-a-uuid," + ownId;
        assertTrue(PluginMessageListener.accepts(echoed, ownId));
    }

    @Test
    void rejectsWhenOwnIdUnknown() {
        // proxy without a resolvable ID cannot authenticate anything (fail-closed)
        assertFalse(PluginMessageListener.accepts(ownId.toString(), null));
    }

    @Test
    void trimsWhitespaceAroundEntries() {
        String echoed = ownId.toString() + " , " + foreignId;
        assertTrue(PluginMessageListener.accepts(echoed, ownId));
    }
}
