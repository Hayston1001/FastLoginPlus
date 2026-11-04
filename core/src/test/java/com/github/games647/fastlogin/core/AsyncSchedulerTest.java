/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class AsyncSchedulerTest {

    @Test
    @DisabledForJreRange(min = JRE.JAVA_21)
    void legacyScheduler() {
        Logger logger = LoggerFactory.getLogger(AsyncSchedulerTest.class);
        AsyncScheduler scheduler = new AsyncScheduler(logger, Executors.newCachedThreadPool());

        AtomicBoolean virtual = new AtomicBoolean(false);
        scheduler.runAsync(() -> setVirtual(virtual)).join();

        Assertions.assertFalse(virtual.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void greenThread() {
        Logger logger = LoggerFactory.getLogger(AsyncSchedulerTest.class);
        AsyncScheduler scheduler = new AsyncScheduler(logger, Executors.newCachedThreadPool());

        AtomicBoolean virtual = new AtomicBoolean(false);
        scheduler.runAsync(() -> setVirtual(virtual)).join();

        Assertions.assertTrue(virtual.get());
    }

    @Test
    void awaitQuiescenceWaitsForTheRunningTask() throws InterruptedException {
        Logger logger = LoggerFactory.getLogger(AsyncSchedulerTest.class);
        AsyncScheduler scheduler = new AsyncScheduler(logger, Executors.newCachedThreadPool());

        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        scheduler.runAsync(() -> {
            started.countDown();
            try {
                Thread.sleep(100L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            finished.set(true);
        });
        Assertions.assertTrue(started.await(5, TimeUnit.SECONDS));

        scheduler.shutdown();
        Assertions.assertTrue(scheduler.awaitQuiescence(Duration.ofSeconds(5)));
        Assertions.assertTrue(finished.get(), "a task that already started must finish");
    }

    @Test
    void delayedTaskIsDroppedAfterShutdown() throws InterruptedException {
        Logger logger = LoggerFactory.getLogger(AsyncSchedulerTest.class);
        AsyncScheduler scheduler = new AsyncScheduler(logger, Executors.newCachedThreadPool());

        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.runAsyncDelayed(() -> ran.set(true), Duration.ofMillis(200L));
        scheduler.shutdown();

        Assertions.assertTrue(scheduler.awaitQuiescence(Duration.ofSeconds(5)),
                "a task sleeping out its delay is not running yet");
        Thread.sleep(300L);
        Assertions.assertFalse(ran.get(), "a task that only wakes up after shutdown must not run");
    }

    private static void setVirtual(AtomicBoolean virtual) {
        boolean isVirtual;
        try {
            isVirtual = (boolean) Thread.class.getDeclaredMethod("isVirtual").invoke(Thread.currentThread());
            if (isVirtual) {
                virtual.set(true);
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            ex.printStackTrace();
        }
    }
}
