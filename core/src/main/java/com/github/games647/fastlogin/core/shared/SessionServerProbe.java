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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.resolver.MojangResolver;

/**
 * Reachability of Mojang's session server (the one that answers
 * {@code hasJoined}), used to tell a client-side verification failure apart
 * from an authentication outage (F-13, R2).
 *
 * <p>The probe is a {@code hasJoined} call with a nonsense name and hash: the
 * session server answers "no such record", which proves it is reachable. It
 * deliberately uses the <em>session</em> host and not the name-lookup host —
 * they are different services and one can be down while the other is fine.</p>
 *
 * <p>Results are cached briefly so an authentication outage costs one probe per
 * window instead of one per failed login; the shared Mojang budget (600 name
 * lookups per 10 minutes) is therefore not a concern.</p>
 */
public final class SessionServerProbe {

    private static final String PROBE_USERNAME = "FastLoginPlus";
    private static final String PROBE_HASH = "flp-reachability-probe";

    private final MojangResolver resolver;
    private final long cacheMillis;

    private volatile long cachedAt;
    private volatile boolean reachable = true;

    /**
     * @param resolver the resolver whose session check reaches the session server
     * @param cacheMillis how long one answer is reused for
     */
    public SessionServerProbe(MojangResolver resolver, long cacheMillis) {
        this.resolver = resolver;
        this.cacheMillis = cacheMillis;
    }

    /**
     * Whether the session server answers right now. A cached answer younger than
     * the cache window is reused; a first call (or an expired one) probes.
     *
     * @return true when the session server answered, false when it could not be reached
     */
    public boolean isReachable() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < cacheMillis) {
            return reachable;
        }

        try {
            resolver.hasJoined(PROBE_USERNAME, PROBE_HASH, null);
            reachable = true;
        } catch (Exception unreachable) {
            // any failure — network, parser, unexpected runtime error — means
            // the session server did not answer us: treat it as unreachable.
            // A bug here must degrade the verdict to "no answer", never throw
            // into the settlement sweep and stall it silently.
            reachable = false;
        }
        cachedAt = now;
        return reachable;
    }
}
