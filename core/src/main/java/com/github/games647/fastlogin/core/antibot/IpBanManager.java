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

import com.google.common.base.Ticker;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages temporary IP bans with automatic expiration.
 *
 * <p>Expired entries are cleaned up lazily during {@link #isBanned}.</p>
 */
public class IpBanManager {

    private final Ticker ticker;
    private final ConcurrentHashMap<InetAddress, Long> bans;

    public IpBanManager(Ticker ticker) {
        this.ticker = ticker;
        this.bans = new ConcurrentHashMap<>();
    }

    /**
     * Ban an IP address for the specified duration.
     *
     * @param address    the IP to ban
     * @param durationMs ban duration in milliseconds
     */
    public void ban(InetAddress address, long durationMs) {
        long nowMs = ticker.read() / 1_000_000;
        bans.put(address, nowMs + durationMs);
    }

    /**
     * Check if an IP address is currently banned.
     *
     * @param address the IP to check
     * @return true if banned and not yet expired
     */
    public boolean isBanned(InetAddress address) {
        Long unbanTime = bans.get(address);
        if (unbanTime == null) {
            return false;
        }

        long nowMs = ticker.read() / 1_000_000;
        if (nowMs >= unbanTime) {
            // expired — remove and return not banned
            bans.remove(address, unbanTime);
            return false;
        }

        return true;
    }

    /**
     * Remove all expired ban entries.
     */
    public void cleanup() {
        long nowMs = ticker.read() / 1_000_000;
        Iterator<Map.Entry<InetAddress, Long>> it = bans.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<InetAddress, Long> entry = it.next();
            if (nowMs >= entry.getValue()) {
                it.remove();
            }
        }
    }

    /**
     * Get the number of currently active bans (including not-yet-expired).
     * Intended for testing and monitoring.
     *
     * @return the number of active ban entries
     */
    public int banCount() {
        return bans.size();
    }
}
