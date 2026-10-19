/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.scheduler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;

public class AsyncScheduler extends AbstractAsyncScheduler {

    public AsyncScheduler(Logger logger, Executor processingPool) {
        super(logger, processingPool);
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        if (isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> process(task), processingPool).exceptionally(error -> {
            logger.warn("Error occurred on thread pool", error);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> runAsyncDelayed(Runnable task, Duration delay) {
        if (isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            currentlyRunning.incrementAndGet();
            try {
                Thread.sleep(delay.toMillis());
                // the plugin may have disabled during the delay
                if (isShutdown()) {
                    return;
                }
                process(task);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } finally {
                currentlyRunning.getAndDecrement();
            }
        }, processingPool).exceptionally(error -> {
            logger.warn("Error occurred on thread pool", error);
            return null;
        });
    }
}
