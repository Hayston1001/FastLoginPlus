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

    /**
     * Wait until every task that already started has finished, up to the given
     * timeout. Submitting stops accepting work after {@link #shutdown()}, but a
     * task that is already running may still be using resources (for example
     * the database pool) that the caller is about to close.
     *
     * @param timeout maximum time to wait
     * @return true when no task is running any more
     */
    public boolean awaitQuiescence(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (currentlyRunning.get() > 0) {
            if (System.nanoTime() >= deadline) {
                logger.warn("{} task(s) still running after the shutdown wait", currentlyRunning.get());
                return false;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    protected boolean isShutdown() {
        return shutdown;
    }

    public abstract CompletableFuture<Void> runAsync(Runnable task);

    public abstract CompletableFuture<Void> runAsyncDelayed(Runnable task, Duration delay);

    protected void process(Runnable task) {
        // second gate: a task accepted before shutdown() but only picked up
        // afterwards must not touch resources the owner has already closed
        if (shutdown) {
            return;
        }
        currentlyRunning.incrementAndGet();
        try {
            task.run();
        } finally {
            currentlyRunning.getAndDecrement();
        }
    }
}
