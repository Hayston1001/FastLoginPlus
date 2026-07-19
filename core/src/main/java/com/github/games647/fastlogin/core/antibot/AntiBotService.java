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

import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class AntiBotService {

    private static final int CLEANUP_INTERVAL = 100;

    private final Logger logger;

    private final boolean enabled;
    private final RateLimiter globalLimiter;
    private final Action limitReachedAction;

    private final TrustedIpSet trustedIpSet;
    private final IpBanManager ipBanManager;
    private final PerIpRateLimiter perIpLimiter;
    private final long banDurationMs;
    private int connectionCount;

    // CHECKSTYLE.OFF: ParameterNumber — 8 params is intentional; enabled flag + all layers
    public AntiBotService(Logger logger, boolean enabled, RateLimiter globalLimiter,
                         Action limitReachedAction, TrustedIpSet trustedIpSet,
                         IpBanManager ipBanManager, PerIpRateLimiter perIpLimiter,
                         long banDurationMs) {
        this.logger = logger;
        this.enabled = enabled;
        this.globalLimiter = globalLimiter;
        this.limitReachedAction = limitReachedAction;
        this.trustedIpSet = trustedIpSet;
        this.ipBanManager = ipBanManager;
        this.perIpLimiter = perIpLimiter;
        this.banDurationMs = banDurationMs;
    }
    // CHECKSTYLE.ON: ParameterNumber

    /**
     * Check an incoming connection through the multi-layer anti-bot pipeline.
     *
     * <p>Check order:
     * <ol>
     *   <li>Trusted IP → allow immediately</li>
     *   <li>Banned IP → reject</li>
     *   <li>Global rate limit → reject if exceeded</li>
     *   <li>Per-IP rate limit → ban + reject if exceeded</li>
     * </ol>
     *
     * @param clientAddress the client's socket address
     * @param username      the player's username
     * @return the action to take
     */
    public Action onIncomingConnection(InetSocketAddress clientAddress, String username) {
        if (!enabled) {
            return Action.Continue;
        }

        // Periodic cleanup every N connections
        if (++connectionCount >= CLEANUP_INTERVAL) {
            connectionCount = 0;
            long nowMs = System.currentTimeMillis();
            perIpLimiter.cleanup(nowMs);
            ipBanManager.cleanup();
        }

        InetAddress address = clientAddress.getAddress();

        // 1. trusted IP — always allow
        if (trustedIpSet.isTrusted(address)) {
            return Action.Continue;
        }

        // 2. banned IP — reject
        if (ipBanManager.isBanned(address)) {
            logUsername("Anti-Bot: banned IP rejected - {} ({})", username, clientAddress);
            return limitReachedAction;
        }

        // 3. global rate limit (check before per-IP to avoid wasting per-IP quota)
        if (!globalLimiter.tryAcquire()) {
            logUsername("Anti-Bot: global join limit - {} ({})", username, clientAddress);
            return limitReachedAction;
        }

        // 4. per-IP rate limit
        if (!perIpLimiter.tryAcquire(address)) {
            ipBanManager.ban(address, banDurationMs);
            logUsername("Anti-Bot: per-IP limit exceeded, banning {} ({}) for {}ms",
                    username, clientAddress, banDurationMs);
            return limitReachedAction;
        }

        return Action.Continue;
    }

    /**
     * Sanitize a username for safe logging.
     * Strips control characters and limits length to prevent log injection.
     *
     * @param username the raw username
     * @return sanitized username safe for logging
     */
    private static String sanitizeUsername(String username) {
        if (username == null) {
            return "<null>";
        }
        // strip non-printable chars (newlines, ANSI escapes, etc.), limit to 32 chars
        String sanitized = username.replaceAll("[\\p{Cc}\\p{Cf}]", "");
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32) + "...";
        }
        return sanitized.isEmpty() ? "<empty>" : sanitized;
    }

    private void logUsername(String format, String username, Object... extraArgs) {
        Object[] args = new Object[extraArgs.length + 1];
        args[0] = sanitizeUsername(username);
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
        logger.warn(format, args);
    }

    public enum Action {
        Ignore,

        Block,

        Continue
    }
}
