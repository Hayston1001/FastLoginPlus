/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * authentication decision for backend -> proxy plugin messages.
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
    void rejectsEmptyEchoedSet() {
        // empty set = backend has no trusted proxy
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
