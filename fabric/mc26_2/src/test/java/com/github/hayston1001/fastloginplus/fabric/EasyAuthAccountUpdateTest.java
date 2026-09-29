/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EasyAuthAccountUpdateTest {

    private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERIFIED = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void failedBindingUpdateRestoresPasswordAccount() {
        Entry entry = new Entry();
        assertThrows(IllegalStateException.class, () -> EasyAuthAccountUpdate.bind(Entry.class, entry,
                VERIFIED, candidate -> {
                    assertEquals("", entry.password);
                    assertEquals(VERIFIED, entry.uuid);
                    return false;
                }));
        assertOriginal(entry);
    }

    @Test
    void thrownLoginUpdateRestoresAllMutableFields() {
        Entry entry = new Entry();
        assertThrows(IllegalStateException.class, () -> EasyAuthAccountUpdate.authenticate(
                Entry.class, entry, VERIFIED, ZonedDateTime.now(), "127.0.0.2", true,
                candidate -> {
                    assertEquals(Kind.TRUE, entry.onlineAccount);
                    assertEquals(0L, entry.loginTries);
                    throw new IllegalStateException("database unavailable");
                }));
        assertOriginal(entry);
    }

    @Test
    void successfulUpdateRetainsPremiumAccount() {
        Entry entry = new Entry();
        EasyAuthAccountUpdate.authenticate(Entry.class, entry, VERIFIED, ZonedDateTime.now(),
                "127.0.0.2", true, candidate -> true);
        assertEquals("", entry.password);
        assertEquals(Kind.TRUE, entry.onlineAccount);
        assertEquals(VERIFIED, entry.uuid);
        assertEquals(0L, entry.loginTries);
        assertEquals("127.0.0.2", entry.lastIp);
    }

    private static void assertOriginal(Entry entry) {
        assertEquals("old-hash", entry.password);
        assertEquals(Kind.FALSE, entry.onlineAccount);
        assertEquals(OFFLINE, entry.uuid);
        assertEquals(ZonedDateTime.parse("2026-01-01T00:00:00Z"), entry.lastAuthenticatedDate);
        assertEquals(2L, entry.loginTries);
        assertEquals("127.0.0.1", entry.lastIp);
    }

    public enum Kind {
        FALSE, TRUE
    }

    public static final class Entry {
        public String password = "old-hash";
        public Kind onlineAccount = Kind.FALSE;
        public UUID uuid = OFFLINE;
        public ZonedDateTime lastAuthenticatedDate = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        public long loginTries = 2;
        public String lastIp = "127.0.0.1";
    }
}
