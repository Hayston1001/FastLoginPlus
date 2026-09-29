/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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
package com.github.hayston1001.fastloginplus.fabric;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BindingCookieProofTest {

    @Test
    void missingResponseFailsOnceAndCannotVerifyLater() {
        Connection connection = mock(Connection.class);
        AtomicInteger verified = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        var request = BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{1},
                verified::incrementAndGet, failed::incrementAndGet);

        BindingCookieProof.expireStoreRequest(connection, request);
        BindingCookieProof.expireStoreRequest(connection, request);

        assertEquals(0, verified.get());
        assertEquals(1, failed.get());
        assertTrue(BindingCookieProof.accept(connection,
                new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{1})));
        assertEquals(0, verified.get());
        assertEquals(1, failed.get());
    }

    @Test
    void wrongCookieFailsClosed() {
        Connection connection = mock(Connection.class);
        AtomicInteger verified = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        var request = BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{1},
                verified::incrementAndGet, failed::incrementAndGet);

        assertTrue(BindingCookieProof.accept(connection,
                new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{2})));
        BindingCookieProof.expireStoreRequest(connection, request);

        assertEquals(0, verified.get());
        assertEquals(1, failed.get());
    }

    @Test
    void disconnectCancelsOutstandingConfirmation() {
        Connection connection = mock(Connection.class);
        AtomicInteger failed = new AtomicInteger();
        var request = BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{1},
                () -> { }, failed::incrementAndGet);

        BindingCookieProof.drop((BindingCookieProof.ConnectionAccess) () -> connection);
        BindingCookieProof.expireStoreRequest(connection, request);

        assertEquals(1, failed.get());
    }

    @Test
    void staleTimeoutCannotCancelNewAttempt() {
        Connection connection = mock(Connection.class);
        AtomicInteger verified = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        var oldRequest = BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{1},
                verified::incrementAndGet, failed::incrementAndGet);
        BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{2},
                verified::incrementAndGet, failed::incrementAndGet);

        BindingCookieProof.expireStoreRequest(connection, oldRequest);
        assertTrue(BindingCookieProof.accept(connection,
                new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{2})));

        assertEquals(1, verified.get());
        assertEquals(0, failed.get());
    }

    @Test
    void overlappingAttemptsCannotTransferFromOldCookieAndCanRetry() {
        Connection connection = mock(Connection.class);
        AtomicInteger verified = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        var oldRequest = BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{1},
                verified::incrementAndGet, failed::incrementAndGet);
        var newRequest = BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{2},
                verified::incrementAndGet, failed::incrementAndGet);

        assertTrue(BindingCookieProof.accept(connection,
                new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{1})));
        BindingCookieProof.expireStoreRequest(connection, oldRequest);
        BindingCookieProof.expireStoreRequest(connection, newRequest);
        assertEquals(0, verified.get());
        assertEquals(1, failed.get());

        BindingCookieProof.registerStoreRequest(connection, "Player", new byte[]{3},
                verified::incrementAndGet, failed::incrementAndGet);
        assertTrue(BindingCookieProof.accept(connection,
                new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{3})));
        assertEquals(1, verified.get());
        assertEquals(1, failed.get());
    }

    @Test
    void cookieFromAnotherConnectionCannotConfirmThisAttempt() {
        Connection first = mock(Connection.class);
        Connection second = mock(Connection.class);
        AtomicInteger verified = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        var request = BindingCookieProof.registerStoreRequest(first, "Player", new byte[]{1},
                verified::incrementAndGet, failed::incrementAndGet);

        assertTrue(BindingCookieProof.accept(second,
                new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{1})));
        assertEquals(0, verified.get());
        assertTrue(BindingCookieProof.accept(first,
                new ServerboundCookieResponsePacket(BindingCookieProof.KEY, new byte[]{1})));
        BindingCookieProof.expireStoreRequest(first, request);
        assertEquals(1, verified.get());
        assertEquals(0, failed.get());
    }

    @Test
    void unrelatedCookieStillUsesVanillaHandling() {
        Connection connection = mock(Connection.class);

        assertFalse(BindingCookieProof.accept(connection, new ServerboundCookieResponsePacket(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("other", "cookie"), new byte[]{1})));
    }
}
