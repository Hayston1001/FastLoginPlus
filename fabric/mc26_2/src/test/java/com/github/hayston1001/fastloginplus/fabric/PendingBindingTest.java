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

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingBindingTest {

    private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void passwordChangeOrWrongAccountInvalidatesWindow() {
        AtomicLong clock = new AtomicLong(100);
        var pending = new EasyAuth344Bridge.PendingBinding(OFFLINE, "old-hash", TARGET, clock::get);

        assertTrue(pending.matchesAccount(OFFLINE, "old-hash"));
        assertFalse(pending.matchesAccount(OFFLINE, "new-hash"));
        assertFalse(pending.matchesAccount(OTHER, "old-hash"));
    }

    @Test
    void onlyThePinnedPremiumIdentityMatches() {
        AtomicLong clock = new AtomicLong(100);
        var pending = new EasyAuth344Bridge.PendingBinding(OFFLINE, "hash", TARGET, clock::get);

        assertTrue(pending.matchesTarget(TARGET));
        assertFalse(pending.matchesTarget(OTHER));
        assertFalse(pending.matchesTarget(null));
    }

    @Test
    void windowExpiresAtDeadlineAndNewAttemptCanStart() {
        AtomicLong clock = new AtomicLong(100);
        var old = new EasyAuth344Bridge.PendingBinding(OFFLINE, "hash", TARGET, clock::get);

        clock.addAndGet(TimeUnit.MINUTES.toNanos(10));
        assertFalse(old.matchesAccount(OFFLINE, "hash"));
        assertFalse(old.matchesTarget(TARGET));
        var retry = new EasyAuth344Bridge.PendingBinding(OFFLINE, "hash", TARGET, clock::get);
        assertTrue(retry.matchesAccount(OFFLINE, "hash"));
        assertTrue(retry.matchesTarget(TARGET));
    }
}
