/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.resolver.ratelimiter;

@FunctionalInterface
public interface RateLimiter {

    boolean tryAcquire();
}
