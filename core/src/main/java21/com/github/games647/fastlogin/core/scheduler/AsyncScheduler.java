/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.scheduler;

import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This limits the number of threads that are used at maximum. Thread creation can be very heavy for the CPU and
 * context switching between threads too. However, we need many threads for blocking HTTP and database calls.
 * Nevertheless, this number can be further limited, because the number of actually working database threads
 * is limited by the size of our database pool. The goal is to separate concerns into processing and blocking only
 * threads.
 */
public class AsyncScheduler extends AbstractAsyncScheduler {

    public AsyncScheduler(Logger logger, Executor processingPool) {
        super(logger, Executors.newVirtualThreadPerTaskExecutor());

        // this variant deliberately replaces the injected platform
        // pool with virtual threads (green threads).  The platform executor is
        // NOT used, so platform-side cancellation cannot reach these tasks —
        // shutdown() is the only cancellation path (called on plugin disable).
        logger.info("Using optimized green threads with Java 21");
    }
}
