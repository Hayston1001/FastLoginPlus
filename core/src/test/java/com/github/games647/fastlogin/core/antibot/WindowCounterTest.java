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
package com.github.games647.fastlogin.core.antibot;

import com.github.games647.fastlogin.core.FakeTicker;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the WindowCounter expiry semantics (0.5.0/F037) and the lazy
 * cleanup throttle (0.5.0/F038) — package-private access required.
 */
class WindowCounterTest {

    private InetAddress ip(String addr) throws UnknownHostException {
        return InetAddress.getByName(addr);
    }

    @Test
    void neverRecordedCounterMustNotBeExpired() {
        // 0.5.0/F037: a brand-new counter (created by computeIfAbsent, before
        // tryRecord ran) must not be reported as expired by cleanup — removing
        // it would race the first tryRecord and lose the record
        WindowCounter counter = new WindowCounter();
        assertFalse(counter.isExpired(1_000, 10_000, 300_000));
        // even at huge offsets (e.g. an epoch/uptime mixup) a fresh counter
        // must not be removed
        assertFalse(counter.isExpired(1_700_000_000_000L, 10_000, 300_000));
    }

    @Test
    void counterExpiresAfterIdleLongerThanLongestWindow() {
        WindowCounter counter = new WindowCounter();
        assertTrue(counter.tryRecord(1_000, 10, 10_000, 1_000, 300_000));
        // idle shorter than the conn window — stays
        assertFalse(counter.isExpired(1_000 + 299_999, 10_000, 300_000));
        // idle longer than the conn window — removable
        assertTrue(counter.isExpired(1_000 + 300_001, 10_000, 300_000));
    }

    @Test
    void lazyCleanupIsThrottledToOncePerSecond() throws UnknownHostException {
        // 0.5.0/F038: with >64 entries the lazy cleanup must not scan the
        // whole map on every acquire
        FakeTicker ticker = new FakeTicker(10_000_000_000L);
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, 100, 10_000, 1000, 300_000);
        for (int i = 0; i < 80; i++) {
            assertTrue(limiter.tryAcquire(ip("10.0.0." + (i + 1))));
        }
        long runs = limiter.lazyCleanupRuns.get();
        assertTrue(runs >= 1, "cleanup should have run once the map grew");

        // hammer more acquisitions without advancing the clock — no further
        // full-map scans allowed
        for (int i = 0; i < 50; i++) {
            limiter.tryAcquire(ip("10.0.1." + (i + 1)));
        }
        assertEquals(runs, limiter.lazyCleanupRuns.get(), "cleanup must be throttled");

        // after advancing the clock the next acquire may clean again
        ticker.add(Duration.ofSeconds(2));
        limiter.tryAcquire(ip("10.0.2.1"));
        assertEquals(runs + 1, limiter.lazyCleanupRuns.get());
    }
}
