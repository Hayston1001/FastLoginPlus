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

    @Test
    void passwordChangeOrWrongAccountInvalidatesWindow() {
        AtomicLong clock = new AtomicLong(100);
        UUID offlineUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var pending = new EasyAuth344Bridge.PendingBinding(offlineUuid, "old-hash", new byte[]{1},
                clock::get);

        assertTrue(pending.matches(offlineUuid, "old-hash"));
        assertFalse(pending.matches(offlineUuid, "new-hash"));
        assertFalse(pending.matches(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "old-hash"));
    }

    @Test
    void windowExpiresAtDeadlineAndNewAttemptCanStart() {
        AtomicLong clock = new AtomicLong(100);
        UUID offlineUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var old = new EasyAuth344Bridge.PendingBinding(offlineUuid, "hash", new byte[]{1}, clock::get);

        clock.addAndGet(TimeUnit.MINUTES.toNanos(10));
        assertFalse(old.matches(offlineUuid, "hash"));
        var retry = new EasyAuth344Bridge.PendingBinding(offlineUuid, "hash", new byte[]{2}, clock::get);
        assertTrue(retry.matches(offlineUuid, "hash"));
    }
}
