/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityClaimReplayCacheTest {

    @Test
    void rejectsTheSecondUseOfOneNonce() {
        IdentityClaimReplayCache cache = new IdentityClaimReplayCache();
        UUID nonce = UUID.randomUUID();
        long now = System.currentTimeMillis();

        assertTrue(cache.register(nonce, now));
        assertFalse(cache.register(nonce, now + 1L));
    }

    @Test
    void forgetsNoncesAfterTheRetentionWindow() {
        IdentityClaimReplayCache cache = new IdentityClaimReplayCache(16, 1_000L);
        UUID nonce = UUID.randomUUID();
        long now = System.currentTimeMillis();

        assertTrue(cache.register(nonce, now));
        // the entry expired, so the same value registers again instead of filling the cache forever
        assertTrue(cache.register(nonce, now + 1_001L));
        assertTrue(cache.size() == 1);
    }

    @Test
    void rejectsNewNoncesWhenFullInsteadOfDroppingLiveEntries() {
        IdentityClaimReplayCache cache = new IdentityClaimReplayCache(2, 60_000L);
        long now = System.currentTimeMillis();

        assertTrue(cache.register(UUID.randomUUID(), now));
        assertTrue(cache.register(UUID.randomUUID(), now));

        assertFalse(cache.register(UUID.randomUUID(), now));
        assertTrue(cache.size() == 2);
    }
}
