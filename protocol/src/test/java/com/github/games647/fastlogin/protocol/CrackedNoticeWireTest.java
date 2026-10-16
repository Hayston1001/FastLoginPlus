/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrackedNoticeWireTest {
    private CrackedNoticeWire capability() {
        return new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE, "TestUser", UUID.randomUUID(),
                UUID.randomUUID() + "," + UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void roundTripPreservesConnectionAndDeduplicationFieldsForBothKinds() throws Exception {
        CrackedNoticeWire expected = capability();
        for (CrackedNoticeWire.Kind kind : CrackedNoticeWire.Kind.values()) {
            CrackedNoticeWire actual = CrackedNoticeWire.decode(expected.withKind(kind).encode());
            assertEquals(kind, actual.kind());
            assertTrue(expected.sameDelivery(actual));
        }
    }

    @Test
    void rejectsTruncationTrailingBytesOversizeAndUnsupportedVersions() {
        byte[] bytes = capability().encode();
        for (int length = 0; length < bytes.length; length++) {
            final byte[] truncated = Arrays.copyOf(bytes, length);
            assertThrows(IOException.class, () -> CrackedNoticeWire.decode(truncated));
        }
        assertThrows(IOException.class, () -> CrackedNoticeWire.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        assertThrows(IOException.class, () -> CrackedNoticeWire.decode(new byte[CrackedNoticeWire.MAX_BYTES + 1]));
        bytes[0] = 2;
        assertThrows(IOException.class, () -> CrackedNoticeWire.decode(bytes));
        bytes[0] = 1;
        bytes[1] = 2;
        assertThrows(IOException.class, () -> CrackedNoticeWire.decode(bytes));
    }

    @Test
    void differentConfirmationsAreNotTheSameDelivery() {
        CrackedNoticeWire first = capability();
        assertFalse(first.sameDelivery(new CrackedNoticeWire(first.kind(), first.name(), first.playerId(),
                first.sourceProxyIds(), UUID.randomUUID())));
        assertFalse(first.sameDelivery(null));
    }
}
