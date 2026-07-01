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
package com.github.games647.fastlogin.core.antibot;

import com.google.common.base.Ticker;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter with dual time windows: a short burst window and a long
 * connection window. Both must pass for a connection to be allowed.
 *
 * <p>Expired entries are cleaned up lazily during {@link #tryAcquire}.</p>
 */
public class PerIpRateLimiter {

    private final Ticker ticker;

    private final int burstLimit;
    private final long burstWindowMs;

    private final int connLimit;
    private final long connWindowMs;

    private final ConcurrentHashMap<InetAddress, WindowCounter> windows;

    public PerIpRateLimiter(Ticker ticker, int burstLimit, long burstWindowMs,
                            int connLimit, long connWindowMs) {
        this.ticker = ticker;
        this.burstLimit = burstLimit;
        this.burstWindowMs = burstWindowMs;
        this.connLimit = connLimit;
        this.connWindowMs = connWindowMs;
        this.windows = new ConcurrentHashMap<>();
    }

    /**
     * Try to allow a connection from the given address.
     *
     * @param address the client IP address
     * @return true if allowed, false if either window limit is exceeded
     */
    public boolean tryAcquire(InetAddress address) {
        long nowMs = ticker.read() / 1_000_000;

        // lazy cleanup: remove expired entries every ~100 accesses
        // (ConcurrentHashMap.size() is cheap enough for this)
        if (windows.mappingCount() > 64) {
            cleanup(nowMs);
        }

        WindowCounter counter = windows.computeIfAbsent(address, k -> new WindowCounter());
        return counter.tryRecord(nowMs, burstLimit, burstWindowMs, connLimit, connWindowMs);
    }

    /**
     * Remove all entries whose both windows have expired.
     *
     * @param nowMs current time in milliseconds
     */
    void cleanup(long nowMs) {
        Iterator<Map.Entry<InetAddress, WindowCounter>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<InetAddress, WindowCounter> entry = it.next();
            if (entry.getValue().isExpired(nowMs, burstWindowMs, connWindowMs)) {
                it.remove();
            }
        }
    }
}
