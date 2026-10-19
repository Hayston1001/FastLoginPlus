/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.resolver.ratelimiter;

import com.google.common.base.Ticker;

import java.time.Duration;

public class FakeTicker extends Ticker {

    private long timestamp;

    public FakeTicker(long initial) {
        timestamp = initial;
    }

    @Override
    public long read() {
        return timestamp;
    }

    public void add(Duration duration) {
        timestamp += duration.toNanos();
    }
}
