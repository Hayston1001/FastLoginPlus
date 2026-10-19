/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.antibot;

import com.google.common.base.Ticker;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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

    // Minimum interval between lazy cleanups triggered from tryAcquire — the
    // full-map scan is O(n) and runs on connection threads, so scanning on
    // every acquire under load is a self-inflicted DoS
    private static final long LAZY_CLEANUP_MIN_INTERVAL_MS = 1_000;

    private volatile long lastLazyCleanupMs;
    // lazy cleanup invocation count — package-private, for tests
    final AtomicLong lazyCleanupRuns = new AtomicLong();

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

        // lazy cleanup: remove expired entries, throttled to once per second
        // (ConcurrentHashMap.mappingCount() is cheap, the scan itself is not)
        if (windows.mappingCount() > 64
                && nowMs - lastLazyCleanupMs >= LAZY_CLEANUP_MIN_INTERVAL_MS) {
            lastLazyCleanupMs = nowMs;
            lazyCleanupRuns.incrementAndGet();
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
    public void cleanup(long nowMs) {
        Iterator<Map.Entry<InetAddress, WindowCounter>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<InetAddress, WindowCounter> entry = it.next();
            if (entry.getValue().isExpired(nowMs, burstWindowMs, connWindowMs)) {
                it.remove();
            }
        }
    }
}
