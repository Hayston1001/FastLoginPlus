/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.scheduler;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractAsyncScheduler {

    protected final Logger logger;
    protected final Executor processingPool;
    protected final AtomicInteger currentlyRunning = new AtomicInteger();

    // after the plugin disabled, no further tasks may run — they
    // would touch resources closed by core.close() (e.g. the HikariDataSource)
    private volatile boolean shutdown;

    public AbstractAsyncScheduler(Logger logger, Executor processingPool) {
        this.logger = logger;
        this.processingPool = processingPool;
    }

    /**
     * Prevent further task execution. Called when the owning plugin disables —
     * platform schedulers cannot cancel tasks submitted through this
     * abstraction, so a flag is the only reliable gate.
     */
    public void shutdown() {
        shutdown = true;
    }

    protected boolean isShutdown() {
        return shutdown;
    }

    public abstract CompletableFuture<Void> runAsync(Runnable task);

    public abstract CompletableFuture<Void> runAsyncDelayed(Runnable task, Duration delay);

    protected void process(Runnable task) {
        currentlyRunning.incrementAndGet();
        try {
            task.run();
        } finally {
            currentlyRunning.getAndDecrement();
        }
    }
}
