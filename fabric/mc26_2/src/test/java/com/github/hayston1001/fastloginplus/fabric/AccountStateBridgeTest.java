/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class AccountStateBridgeTest {
    private static final String NAME = "TestUser";
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private static final UUID VERIFIED = UUID.fromString("abcdef12-abcd-4abc-8def-123456789abc");
    private EasyAuth344Bridge bridge;

    @BeforeEach
    void setup() throws Exception {
        EasyAuth.DB = new Database();
        bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        set("easyAuth", EasyAuth.class);
        set("entryType", Entry.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "TRUE, hash, verified, BOUND", "TRUE, '', verified, BOUND", "TRUE, hash, offline, BOUND",
        "TRUE, hash, null, CONFLICT", "TRUE, '', null, CONFLICT",
        "FALSE, hash, offline, PASSWORD", "UNKNOWN, hash, offline, PASSWORD",
        "FALSE, hash, verified, CONFLICT", "UNKNOWN, hash, verified, CONFLICT",
        "FALSE, hash, null, CONFLICT", "UNKNOWN, hash, null, CONFLICT",
        "FALSE, '', offline, CONFLICT", "FALSE, '', null, CONFLICT",
        "UNKNOWN, '', offline, OPEN", "UNKNOWN, '', null, OPEN", "UNKNOWN, '', verified, OPEN",
        "OTHER, hash, offline, CONFLICT", "OTHER, '', null, CONFLICT",
        "TRUE, null, verified, CONFLICT", "UNKNOWN, null, offline, CONFLICT"
    }, nullValues = "null")
    void classificationMatrixPrioritizesPremiumAndKeepsMalformedAccountsClosed(
            String kind, String password, String uuidKind, AccountState expected) {
        Entry entry = EasyAuth.DB.entry;
        entry.onlineAccount = kind;
        entry.password = password;
        entry.uuid = "offline".equals(uuidKind) ? OFFLINE : "verified".equals(uuidKind) ? VERIFIED : null;
        assertEquals(new AccountState.Snapshot(expected, expected == AccountState.BOUND ? entry.uuid : null),
                bridge.accountStatus(NAME));
    }

    @Test
    void missingAccountIsOpenButMismatchedStoredNameConflicts() {
        EasyAuth.DB.entry = null;
        assertEquals(AccountState.OPEN, bridge.accountStatus(NAME).state());
        EasyAuth.DB.entry = new Entry();
        EasyAuth.DB.entry.username = "OtherUser";
        assertEquals(AccountState.CONFLICT, bridge.accountStatus(NAME).state());
    }

    private void set(String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    public static final class EasyAuth {
        public static Database DB;
    }

    public static final class Database {
        Entry entry = new Entry();

        public Entry getUserData(String name) {
            assertEquals(NAME, name);
            return entry;
        }
    }

    public static final class Entry {
        public String username = NAME;
        public String password = "hash";
        public UUID uuid = OFFLINE;
        public String onlineAccount = "FALSE";
    }
}
