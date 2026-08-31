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

/**
 * Tracks connection counts for a single IP address in two time windows:
 * a short burst window and a long connection window.
 *
 * <p>Thread-safe: all methods are synchronized.</p>
 */
class WindowCounter {

    private int burstCount;
    private long burstStart;

    private int connCount;
    private long connStart;

    // timestamp of the last record attempt (or -1 before the first attempt) —
    // drives expiry so a brand-new counter is never instantly "expired" and
    // removed between computeIfAbsent and tryRecord (0.5.0/F037)
    private long lastRecordMs = -1;

    /**
     * Try to record a connection in both windows.
     *
     * @param now           current time in milliseconds (from Ticker)
     * @param burstLimit    max connections in the burst window
     * @param burstWindowMs burst window duration in milliseconds
     * @param connLimit     max connections in the long window
     * @param connWindowMs  long window duration in milliseconds
     * @return true if allowed, false if either window is exceeded
     */
    synchronized boolean tryRecord(long now, int burstLimit, long burstWindowMs,
                                   int connLimit, long connWindowMs) {
        lastRecordMs = now;

        // --- burst window ---
        if (burstCount == 0 || now - burstStart >= burstWindowMs) {
            // window expired or first use — reset
            burstStart = now;
            burstCount = 0;
        }

        if (burstCount >= burstLimit) {
            return false;
        }

        // --- long connection window ---
        if (connCount == 0 || now - connStart >= connWindowMs) {
            connStart = now;
            connCount = 0;
        }

        if (connCount >= connLimit) {
            return false;
        }

        // both windows have room
        burstCount++;
        connCount++;
        return true;
    }

    /**
     * Check if both windows have fully expired (for lazy cleanup).
     *
     * @param now           current time in milliseconds
     * @param burstWindowMs burst window duration
     * @param connWindowMs  long window duration
     * @return true if this counter can be safely removed
     */
    synchronized boolean isExpired(long now, long burstWindowMs, long connWindowMs) {
        // a counter that never recorded must stay (removing it would race the
        // tryRecord that created it); otherwise expire on idle time since the
        // last record attempt, using the longer of the two windows
        if (lastRecordMs < 0) {
            return false;
        }
        return now - lastRecordMs >= Math.max(burstWindowMs, connWindowMs);
    }
}
