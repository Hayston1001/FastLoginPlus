/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.scheduler;

import java.util.concurrent.Executor;

import org.slf4j.Logger;

public class AsyncScheduler extends AbstractAsyncScheduler {

    public AsyncScheduler(Logger logger, Executor processingPool) {
        super(logger, processingPool);
    }
}
