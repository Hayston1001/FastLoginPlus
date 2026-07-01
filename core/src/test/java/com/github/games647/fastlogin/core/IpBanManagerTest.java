/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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

import com.github.games647.fastlogin.core.antibot.IpBanManager;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpBanManagerTest {

    @Test
    void bannedIpShouldBeDetected() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        IpBanManager manager = new IpBanManager(ticker);
        InetAddress ip = InetAddress.getByName("1.2.3.4");

        manager.ban(ip, 60_000);
        assertTrue(manager.isBanned(ip));
    }

    @Test
    void unbannedIpShouldPass() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        IpBanManager manager = new IpBanManager(ticker);
        InetAddress ip = InetAddress.getByName("1.2.3.4");

        assertFalse(manager.isBanned(ip));
    }

    @Test
    void banShouldExpire() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        IpBanManager manager = new IpBanManager(ticker);
        InetAddress ip = InetAddress.getByName("1.2.3.4");

        manager.ban(ip, 60_000); // 60 seconds ban
        assertTrue(manager.isBanned(ip));

        // advance past ban duration
        ticker.add(Duration.ofSeconds(61));
        assertFalse(manager.isBanned(ip));
    }

    @Test
    void banShouldNotExpireEarly() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        IpBanManager manager = new IpBanManager(ticker);
        InetAddress ip = InetAddress.getByName("1.2.3.4");

        manager.ban(ip, 60_000);

        // advance but not past ban duration
        ticker.add(Duration.ofSeconds(59));
        assertTrue(manager.isBanned(ip));
    }

    @Test
    void differentIpsAreIndependent() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        IpBanManager manager = new IpBanManager(ticker);
        InetAddress ip1 = InetAddress.getByName("1.2.3.4");
        InetAddress ip2 = InetAddress.getByName("5.6.7.8");

        manager.ban(ip1, 60_000);

        assertTrue(manager.isBanned(ip1));
        assertFalse(manager.isBanned(ip2));
    }

    @Test
    void cleanupShouldRemoveExpiredBans() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        IpBanManager manager = new IpBanManager(ticker);
        InetAddress ip = InetAddress.getByName("1.2.3.4");

        manager.ban(ip, 60_000);
        assertTrue(manager.banCount() == 1);

        ticker.add(Duration.ofSeconds(61));
        manager.cleanup();
        assertTrue(manager.banCount() == 0);
    }

    @Test
    void reBanShouldExtendDuration() throws UnknownHostException {
        FakeTicker ticker = new FakeTicker(0);
        IpBanManager manager = new IpBanManager(ticker);
        InetAddress ip = InetAddress.getByName("1.2.3.4");

        manager.ban(ip, 60_000); // ban until t=60s
        ticker.add(Duration.ofSeconds(30));
        manager.ban(ip, 60_000); // re-ban at t=30s, now expires at t=90s

        // at t=60s: original ban would have expired, but re-ban extends to t=90s
        ticker.add(Duration.ofSeconds(30)); // t=60s
        assertTrue(manager.isBanned(ip), "Should still be banned due to re-ban");

        // at t=91s: re-ban expires
        ticker.add(Duration.ofSeconds(31));
        assertFalse(manager.isBanned(ip), "Should be unbanned after extended duration");
    }
}
