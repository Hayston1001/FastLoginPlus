/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;

public class AsyncScheduler extends AbstractAsyncScheduler {

    private static boolean isJava21Plus() {
        try {
            String version = System.getProperty("java.specification.version");
            if (version != null) {
                int major = version.startsWith("1.")
                        ? Integer.parseInt(version.substring(2))
                        : Integer.parseInt(version.split("\\.")[0]);
                return major >= 21;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Executor newVirtualThreadExecutor() {
        try {
            Class<?> executorsClass = Class.forName("java.util.concurrent.Executors");
            Method method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
            return (Executor) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create virtual thread executor", e);
        }
    }

    public AsyncScheduler(Logger logger, Executor processingPool) {
        super(logger, isJava21Plus() ? newVirtualThreadExecutor() : processingPool);
        if (isJava21Plus()) {
            logger.info("Using optimized green threads with Java 21+");
        } else {
            logger.info("Using legacy platform scheduler for using an older Java version. "
                    + "Upgrade Java to 21+ for improved performance");
        }
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(() -> process(task), processingPool).exceptionally(error -> {
            logger.warn("Error occurred on thread pool", error);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> runAsyncDelayed(Runnable task, Duration delay) {
        return CompletableFuture.runAsync(() -> {
            currentlyRunning.incrementAndGet();
            try {
                Thread.sleep(delay.toMillis());
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
