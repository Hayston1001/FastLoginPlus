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
package com.github.games647.fastlogin.core;

import com.github.games647.fastlogin.core.antibot.PerIpRateLimiter;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerIpRateLimiterTest {

    private InetAddress ip(String addr) throws UnknownHostException {
        return InetAddress.getByName(addr);
    }

    @Test
    void firstRequestShouldPass() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, 5, 10_000, 20, 300_000);

        assertTrue(limiter.tryAcquire(ip("1.2.3.4")));
    }

    @Test
    void burstLimitShouldBlock() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        int burstLimit = 3;
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, burstLimit, 10_000, 20, 300_000);

        InetAddress addr = ip("1.2.3.4");
        for (int i = 0; i < burstLimit; i++) {
            assertTrue(limiter.tryAcquire(addr), "Request " + (i + 1) + " should pass");
        }

        assertFalse(limiter.tryAcquire(addr), "Should be blocked after burst limit");
    }

    @Test
    void burstWindowShouldReset() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        int burstLimit = 3;
        long burstWindowMs = 10_000;
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, burstLimit, burstWindowMs, 20, 300_000);

        InetAddress addr = ip("1.2.3.4");
        for (int i = 0; i < burstLimit; i++) {
            limiter.tryAcquire(addr);
        }
        assertFalse(limiter.tryAcquire(addr));

        // advance past burst window
        ticker.add(Duration.ofMillis(burstWindowMs + 1));
        assertTrue(limiter.tryAcquire(addr), "Should pass after burst window reset");
    }

    @Test
    void connLimitShouldBlock() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        int connLimit = 5;
        // use very long burst window so it doesn't interfere
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, 1000, 600_000, connLimit, 300_000);

        InetAddress addr = ip("1.2.3.4");
        for (int i = 0; i < connLimit; i++) {
            assertTrue(limiter.tryAcquire(addr), "Request " + (i + 1) + " should pass");
        }

        assertFalse(limiter.tryAcquire(addr), "Should be blocked after conn limit");
    }

    @Test
    void connWindowShouldReset() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        int connLimit = 3;
        long connWindowMs = 300_000;
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, 1000, 600_000, connLimit, connWindowMs);

        InetAddress addr = ip("1.2.3.4");
        for (int i = 0; i < connLimit; i++) {
            limiter.tryAcquire(addr);
        }
        assertFalse(limiter.tryAcquire(addr));

        // advance past conn window
        ticker.add(Duration.ofMillis(connWindowMs + 1));
        assertTrue(limiter.tryAcquire(addr), "Should pass after conn window reset");
    }

    @Test
    void differentIpsAreIndependent() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        int burstLimit = 1;
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, burstLimit, 10_000, 20, 300_000);

        InetAddress addr1 = ip("1.2.3.4");
        InetAddress addr2 = ip("5.6.7.8");

        assertTrue(limiter.tryAcquire(addr1));
        assertFalse(limiter.tryAcquire(addr1), "IP1 should be blocked");

        assertTrue(limiter.tryAcquire(addr2), "IP2 should still pass");
    }

    @Test
    void burstAndConnLimitsAreBothEnforced() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        // burst limit: 2 in 10s, conn limit: 5 in 5min
        PerIpRateLimiter limiter = new PerIpRateLimiter(ticker, 2, 10_000, 5, 300_000);

        InetAddress addr = ip("1.2.3.4");

        // fill burst window (2 requests)
        assertTrue(limiter.tryAcquire(addr));
        assertTrue(limiter.tryAcquire(addr));
        assertFalse(limiter.tryAcquire(addr), "Burst limit hit");

        // wait for burst window to reset, but conn window still holds
        ticker.add(Duration.ofMillis(10_001));
        assertTrue(limiter.tryAcquire(addr));
        assertTrue(limiter.tryAcquire(addr));
        assertFalse(limiter.tryAcquire(addr), "Burst limit hit again");

        // after 3 total conn requests, we have 4, one more then conn limit
        ticker.add(Duration.ofMillis(10_001));
        assertTrue(limiter.tryAcquire(addr)); // 5th conn request
        assertFalse(limiter.tryAcquire(addr), "Conn limit hit");
    }
}
