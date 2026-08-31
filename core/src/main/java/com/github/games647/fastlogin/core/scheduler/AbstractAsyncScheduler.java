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
package com.github.games647.fastlogin.core.scheduler;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractAsyncScheduler {

    protected final Logger logger;
    protected final Executor processingPool;
    // 0.6.0/F066: task bookkeeping; readable for tests/monitoring via
    // {@link #currentlyRunningTasks()} (previously a write-only counter)
    protected final AtomicInteger currentlyRunning = new AtomicInteger();

    // 0.6.0/F062: tasks accepted (but possibly not yet started) — shutdown()
    // waits for this to drain so no accepted task can outlive the close of
    // shared resources (e.g. the HikariDataSource)
    protected final AtomicInteger submittedTasks = new AtomicInteger();

    // 0.6.0/F062: serializes the shutdown flag flip against task acceptance
    // so the check-then-act race cannot submit a task after the gate closed
    protected final ReentrantLock stateLock = new ReentrantLock();

    // 0.5.0/F046: after the plugin disabled, no further tasks may run — they
    // would touch resources closed by core.close() (e.g. the HikariDataSource)
    private volatile boolean shutdown;

    private static final int SHUTDOWN_DRAIN_SECONDS = 10;
    private static final long SHUTDOWN_POLL_MILLIS = 10L;

    public AbstractAsyncScheduler(Logger logger, Executor processingPool) {
        this.logger = logger;
        this.processingPool = processingPool;
    }

    /**
     * Prevent further task execution and wait (bounded) for already accepted
     * tasks to finish. Called when the owning plugin disables — platform
     * schedulers cannot cancel tasks submitted through this abstraction, so a
     * flag plus a drain wait is the only reliable gate (0.6.0/F062).
     */
    public void shutdown() {
        stateLock.lock();
        try {
            shutdown = true;
        } finally {
            stateLock.unlock();
        }

        long deadlineNanos = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(SHUTDOWN_DRAIN_SECONDS);
        while (submittedTasks.get() > 0 && System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(SHUTDOWN_POLL_MILLIS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    protected boolean isShutdown() {
        return shutdown;
    }

    /**
     * Get the number of tasks currently executing.
     *
     * <p>0.6.0/F066: gives the previously write-only {@code currentlyRunning}
     * counter a reader for tests and monitoring.</p>
     *
     * @return the number of tasks whose {@code process} body is running
     */
    public int currentlyRunningTasks() {
        return currentlyRunning.get();
    }

    /**
     * Atomically (with respect to {@link #shutdown()}) accept a task: the
     * shutdown check and the in-flight increment happen under the same lock
     * as the flag flip, so a task either sees shutdown and is dropped, or
     * {@link #shutdown()} waits for it (0.6.0/F062).
     *
     * @return true if the task may be submitted
     */
    protected final boolean tryAcceptTask() {
        stateLock.lock();
        try {
            if (shutdown) {
                return false;
            }

            submittedTasks.incrementAndGet();
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Mark a previously accepted task as finished.
     */
    protected final void releaseTask() {
        submittedTasks.decrementAndGet();
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
