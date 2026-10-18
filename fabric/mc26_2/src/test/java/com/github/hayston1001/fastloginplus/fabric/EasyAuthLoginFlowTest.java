/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyAuthLoginFlowTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-09-30T12:00:00Z");
    private static final UUID VERIFIED = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void activeLoginTimeoutCannotWriteOrCompleteSession() {
        assertRejected(new EasyAuthLoginFlow.LoginContext(NOW, NOW.minusSeconds(29), 30,
                "127.0.0.2", "127.0.0.1"), "login timeout");
    }

    @Test
    void missingKickDateFailsClosedBeforeWriting() {
        assertRejected(new EasyAuthLoginFlow.LoginContext(NOW, null, 30,
                "127.0.0.2", "127.0.0.1"), "login timeout");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingResolvedIpCannotWriteOrCompleteSession(String ip) {
        assertRejected(new EasyAuthLoginFlow.LoginContext(NOW, NOW.minusSeconds(30), 30,
                ip, "127.0.0.1"), "player IP");
    }

    @Test
    void missingStoredIpCannotWriteOrCompleteSession() {
        assertRejected(new EasyAuthLoginFlow.LoginContext(NOW, NOW.minusSeconds(30), 30,
                "127.0.0.2", null), "player IP");
    }

    @Test
    void expiredTimeoutPersistsBeforeCompletingSession() {
        var entry = new EasyAuthAccountUpdateTest.Entry();
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        EasyAuthLoginFlow.authenticate(entry.getClass(), entry, VERIFIED, validContext(),
                candidate -> {
                    saved.incrementAndGet();
                    assertPremium(entry);
                    assertEquals(0, finished.get());
                    return true;
                }, () -> {
                    assertEquals(1, saved.get());
                    finished.incrementAndGet();
                });
        assertEquals(1, finished.get());
    }

    @Test
    void rejectedWriteRollsBackAndCannotCompleteSession() {
        assertWriteFailure(false);
    }

    @Test
    void thrownWriteRollsBackAndCannotCompleteSession() {
        assertWriteFailure(true);
    }

    @Test
    void postSaveSessionFailureRetainsPersistedBindingForReconnect() {
        var entry = new EasyAuthAccountUpdateTest.Entry();
        AtomicInteger saved = new AtomicInteger();
        IllegalStateException injected = new IllegalStateException("session completion unavailable");
        var failure = assertThrows(IllegalStateException.class, () -> EasyAuthLoginFlow.authenticate(
                entry.getClass(), entry, VERIFIED, validContext(),
                candidate -> {
                    saved.incrementAndGet();
                    return true;
                }, () -> {
                    throw injected;
                }));
        assertSame(injected, failure);
        assertEquals(1, saved.get());
        assertPremium(entry);
    }

    private static void assertRejected(EasyAuthLoginFlow.LoginContext context, String reason) {
        var entry = new EasyAuthAccountUpdateTest.Entry();
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        var failure = assertThrows(IllegalStateException.class, () -> EasyAuthLoginFlow.authenticate(
                entry.getClass(), entry, VERIFIED, context,
                candidate -> {
                    saved.incrementAndGet();
                    return true;
                }, finished::incrementAndGet));
        assertTrue(failure.getMessage().contains(reason));
        assertEquals(0, saved.get());
        assertEquals(0, finished.get());
        assertOriginal(entry);
    }

    private static void assertWriteFailure(boolean throwing) {
        var entry = new EasyAuthAccountUpdateTest.Entry();
        AtomicInteger finished = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> EasyAuthLoginFlow.authenticate(
                entry.getClass(), entry, VERIFIED, validContext(),
                candidate -> {
                    if (throwing) {
                        throw new IllegalStateException("database unavailable");
                    }
                    return false;
                }, finished::incrementAndGet));
        assertEquals(0, finished.get());
        assertOriginal(entry);
    }

    private static EasyAuthLoginFlow.LoginContext validContext() {
        return new EasyAuthLoginFlow.LoginContext(NOW, NOW.minusSeconds(30), 30, "127.0.0.2", "127.0.0.1");
    }

    private static void assertOriginal(EasyAuthAccountUpdateTest.Entry entry) {
        assertEquals("old-hash", entry.password);
        assertEquals(EasyAuthAccountUpdateTest.Kind.FALSE, entry.onlineAccount);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), entry.uuid);
        assertEquals(ZonedDateTime.parse("2026-01-01T00:00:00Z"), entry.lastAuthenticatedDate);
        assertEquals(2L, entry.loginTries);
        assertEquals("127.0.0.1", entry.lastIp);
    }

    private static void assertPremium(EasyAuthAccountUpdateTest.Entry entry) {
        assertEquals("old-hash", entry.password);
        assertEquals(EasyAuthAccountUpdateTest.Kind.TRUE, entry.onlineAccount);
        assertEquals(VERIFIED, entry.uuid);
        assertEquals(NOW, entry.lastAuthenticatedDate);
        assertEquals(0L, entry.loginTries);
        assertEquals("127.0.0.2", entry.lastIp);
    }
}
