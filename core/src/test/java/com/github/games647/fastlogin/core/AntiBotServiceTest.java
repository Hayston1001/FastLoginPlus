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

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.antibot.IpBanManager;
import com.github.games647.fastlogin.core.antibot.PerIpRateLimiter;
import com.github.games647.fastlogin.core.antibot.TickingRateLimiter;
import com.github.games647.fastlogin.core.antibot.TrustedIpSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntiBotServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(AntiBotServiceTest.class);

    private InetSocketAddress addr(String ip) {
        return new InetSocketAddress(ip, 25565);
    }

    // --- enabled flag ---
    @Test
    void disabledServiceShouldAlwaysContinue() {
        FakeTicker ticker = new FakeTicker(0);
        AntiBotService service = new AntiBotService(
                logger, false, () -> true, Action.Block,
                new TrustedIpSet(Collections.emptySet()),
                new IpBanManager(ticker),
                new PerIpRateLimiter(ticker, 1, 1000, 1, 1000),
                60_000
        );

        // Even after many connections, disabled service always allows
        for (int i = 0; i < 100; i++) {
            assertEquals(Action.Continue, service.onIncomingConnection(addr("1.2.3.4"), "bot" + i));
        }
    }

    @Test
    void enabledServiceShouldEnforceLimits() {
        FakeTicker ticker = new FakeTicker(0);
        AntiBotService service = new AntiBotService(
                logger, true, () -> true, Action.Block,
                new TrustedIpSet(Collections.emptySet()),
                new IpBanManager(ticker),
                new PerIpRateLimiter(ticker, 2, 10_000, 100, 300_000),
                60_000
        );

        // First 2 should pass (burst limit = 2)
        assertEquals(Action.Continue, service.onIncomingConnection(addr("1.2.3.4"), "user1"));
        assertEquals(Action.Continue, service.onIncomingConnection(addr("1.2.3.4"), "user2"));
        // 3rd should be blocked -> banned
        assertEquals(Action.Block, service.onIncomingConnection(addr("1.2.3.4"), "user3"));
        // Now banned
        assertEquals(Action.Block, service.onIncomingConnection(addr("1.2.3.4"), "user4"));
    }

    // --- check order (global before per-IP) ---
    @Test
    void globalLimitShouldNotConsumePerIpQuota() {
        FakeTicker ticker = new FakeTicker(0);
        // global limit = 1, per-IP burst = 10
        TickingRateLimiter globalLimit = new TickingRateLimiter(ticker, 1, 60_000);
        AntiBotService service = new AntiBotService(
                logger, true, globalLimit, Action.Ignore,
                new TrustedIpSet(Collections.emptySet()),
                new IpBanManager(ticker),
                new PerIpRateLimiter(ticker, 10, 10_000, 100, 300_000),
                60_000
        );

        // First request from IP A passes both
        assertEquals(Action.Continue, service.onIncomingConnection(addr("1.2.3.4"), "user1"));
        // Second request from IP A: global limit hit -> Ignore (not Block)
        assertEquals(Action.Ignore, service.onIncomingConnection(addr("1.2.3.4"), "user2"));

        // IP A should NOT be banned (global rejection shouldn't trigger per-IP ban)
        // Advance time to expire global limiter
        ticker.add(Duration.ofSeconds(61));
        assertEquals(Action.Continue, service.onIncomingConnection(addr("1.2.3.4"), "user3"));
    }

    // --- Trusted IP bypass ---
    @Test
    void trustedIpShouldBypassAllChecks() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        InetAddress trusted = InetAddress.getByName("10.0.0.1");
        AntiBotService service = new AntiBotService(
                logger, true, () -> false, Action.Block,  // global always rejects
                new TrustedIpSet(Collections.singleton(trusted)),
                new IpBanManager(ticker),
                new PerIpRateLimiter(ticker, 0, 1000, 0, 1000),  // per-IP always rejects
                60_000
        );

        // Trusted IP should pass even though all limiters would reject
        for (int i = 0; i < 100; i++) {
            assertEquals(Action.Continue,
                    service.onIncomingConnection(new InetSocketAddress(trusted, 25565), "admin"));
        }
    }

    // --- username sanitization ---
    @Test
    void maliciousUsernameShouldNotCrashLogging() {
        FakeTicker ticker = new FakeTicker(0);
        AntiBotService service = new AntiBotService(
                logger, true, () -> true, Action.Block,
                new TrustedIpSet(Collections.emptySet()),
                new IpBanManager(ticker),
                new PerIpRateLimiter(ticker, 1, 1000, 1, 1000),
                60_000
        );

        // First call passes (burst=1 allows 1), 2nd call hits per-IP limit -> Block + logging
        assertEquals(Action.Continue,
                service.onIncomingConnection(addr("1.2.3.4"), "evil\n\ruser"));
        assertEquals(Action.Block,
                service.onIncomingConnection(addr("1.2.3.4"), "\u001b[31mred\u001b[0m"));
        // IP is now banned — 3rd call should also be blocked
        StringBuilder longName = new StringBuilder(200);
        for (int i = 0; i < 200; i++) {
            longName.append('A');
        }
        assertEquals(Action.Block,
                service.onIncomingConnection(addr("1.2.3.4"), longName.toString()));
    }
}
