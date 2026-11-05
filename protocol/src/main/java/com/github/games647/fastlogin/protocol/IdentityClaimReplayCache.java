/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Remembers identity-claim nonces for the freshness window so a captured
 * claim cannot be presented twice.
 *
 * <p>The cache is bounded. When it is full after pruning expired entries new
 * nonces are rejected: a full cache must not silently start accepting
 * replays.</p>
 */
public final class IdentityClaimReplayCache {

    /** Enough for a large network's login rate inside one freshness window. */
    public static final int DEFAULT_CAPACITY = 16_384;

    private final int capacity;
    private final long retentionMillis;
    private final Map<UUID, Long> seen = new LinkedHashMap<>();

    public IdentityClaimReplayCache() {
        this(DEFAULT_CAPACITY, IdentityClaim.MAX_AGE_MILLIS);
    }

    /**
     * @param capacity maximum remembered nonces
     * @param retentionMillis how long one nonce stays remembered
     */
    public IdentityClaimReplayCache(int capacity, long retentionMillis) {
        if (capacity <= 0 || retentionMillis <= 0L) {
            throw new IllegalArgumentException("Cache capacity and retention must be positive");
        }
        this.capacity = capacity;
        this.retentionMillis = retentionMillis;
    }

    /**
     * Records a nonce.
     *
     * @param nonce the claim nonce
     * @param now current time in epoch milliseconds
     * @return true when the nonce is new and the claim may be accepted
     */
    public synchronized boolean register(UUID nonce, long now) {
        Objects.requireNonNull(nonce, "nonce");
        prune(now);
        if (seen.containsKey(nonce)) {
            return false;
        }
        if (seen.size() >= capacity) {
            return false;
        }
        seen.put(nonce, now);
        return true;
    }

    /**
     * @return number of remembered nonces
     */
    public synchronized int size() {
        return seen.size();
    }

    private void prune(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < now - retentionMillis) {
                iterator.remove();
            }
        }
    }
}
