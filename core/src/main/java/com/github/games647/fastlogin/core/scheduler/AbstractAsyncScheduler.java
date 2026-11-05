/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.scheduler;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractAsyncScheduler {

    protected final Logger logger;
    protected final Executor processingPool;
    protected final AtomicInteger currentlyRunning = new AtomicInteger();

    // delayed work waits here and is only handed to processingPool once the delay
    // expired - sleeping on a worker thread would pin it for the whole delay
    private final ScheduledExecutorService delayScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "FastLoginPlus-Delayed");
        thread.setDaemon(true);
        return thread;
    });

    private final Set<DelayedTask> pendingDelayed = ConcurrentHashMap.newKeySet();

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
     * abstraction, so a flag is the only reliable gate. Delayed tasks that have
     * not expired yet are dropped here; their futures complete normally.
     */
    public void shutdown() {
        shutdown = true;
        // pending delays must not wake up and submit work after the signal
        delayScheduler.shutdownNow();
        for (DelayedTask delayed : pendingDelayed) {
            pendingDelayed.remove(delayed);
            if (delayed.scheduled != null) {
                delayed.scheduled.cancel(false);
            }
            delayed.result.complete(null);
        }
    }

    /**
     * Wait until every task that already started has finished, up to the given
     * timeout. Submitting stops accepting work after {@link #shutdown()}, but a
     * task that is already running may still be using resources (for example
     * the database pool) that the caller is about to close. A delayed task that
     * has not expired yet is not running and therefore not waited for.
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

    public CompletableFuture<Void> runAsync(Runnable task) {
        if (shutdown) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        submit(task, result);
        return result;
    }

    public CompletableFuture<Void> runAsyncDelayed(Runnable task, Duration delay) {
        if (shutdown) {
            return CompletableFuture.completedFuture(null);
        }

        DelayedTask delayed = new DelayedTask(task);
        pendingDelayed.add(delayed);
        try {
            delayed.scheduled = delayScheduler.schedule(() -> expire(delayed),
                    delay.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException rejected) {
            // shutdown() won the race between the check above and the schedule call
            pendingDelayed.remove(delayed);
            delayed.result.complete(null);
        }
        return delayed.result;
    }

    private void expire(DelayedTask delayed) {
        // no longer pending: shutdown() only completes tasks that never reached
        // this point, so a task already handed to the pool keeps its future
        pendingDelayed.remove(delayed);
        if (shutdown) {
            delayed.result.complete(null);
            return;
        }
        submit(delayed.task, delayed.result);
    }

    private void submit(Runnable task, CompletableFuture<Void> result) {
        try {
            CompletableFuture.runAsync(() -> process(task), processingPool)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            logger.warn("Error occurred on thread pool", error);
                        }
                        result.complete(null);
                    });
        } catch (RuntimeException rejected) {
            // the platform executor may already be shutting down; isolate the
            // failure like one raised by the task itself
            logger.warn("Error occurred on thread pool", rejected);
            result.complete(null);
        }
    }

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

    private static final class DelayedTask {
        private final Runnable task;
        private final CompletableFuture<Void> result = new CompletableFuture<>();
        private volatile ScheduledFuture<?> scheduled;

        private DelayedTask(Runnable task) {
            this.task = task;
        }
    }
}
