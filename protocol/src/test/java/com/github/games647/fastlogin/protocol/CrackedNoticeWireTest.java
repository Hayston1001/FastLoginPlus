/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrackedNoticeWireTest {
    private CrackedNoticeWire capability() {
        return new CrackedNoticeWire(CrackedNoticeWire.Kind.ACK, "TestUser", UUID.randomUUID(),
                UUID.randomUUID() + "," + UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void roundTripPreservesConnectionFieldsForMarkerAndAcknowledgement() throws Exception {
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
        bytes[1] = 1;
        assertThrows(IOException.class, () -> CrackedNoticeWire.decode(bytes));
        bytes[1] = 4;
        assertThrows(IOException.class, () -> CrackedNoticeWire.decode(bytes));
    }

    @Test
    void differentConfirmationsAreNotTheSameDelivery() {
        CrackedNoticeWire first = capability();
        assertFalse(first.sameDelivery(new CrackedNoticeWire(first.kind(), first.name(), first.playerId(),
                first.sourceProxyIds(), UUID.randomUUID())));
        assertFalse(first.sameDelivery(null));
    }

    @Test
    void renderingAnnouncementKeepsTheLegacyCapableWireShape() throws Exception {
        CrackedNoticeWire marker = capability().withKind(CrackedNoticeWire.Kind.CAPABLE);
        // The pre-extension reader accepted only ordinal codes 0/1 followed by these exact fields.
        try (DataInputStream legacy = new DataInputStream(new ByteArrayInputStream(marker.encode()))) {
            assertEquals(1, legacy.readUnsignedByte());
            int kind = legacy.readUnsignedByte();
            assertTrue(kind < 2);
            assertEquals(0, kind);
            assertEquals(marker.name(), legacy.readUTF());
            assertEquals(marker.playerId(), new UUID(legacy.readLong(), legacy.readLong()));
            assertEquals(marker.sourceProxyIds(), legacy.readUTF());
            assertEquals(marker.nonce(), new UUID(legacy.readLong(), legacy.readLong()));
            assertEquals(0, legacy.available());
        }
        assertEquals(CrackedNoticeWire.Kind.CAPABLE, CrackedNoticeWire.decode(marker.encode()).kind());
        assertEquals(3, capability().encode()[1]);
    }
}
