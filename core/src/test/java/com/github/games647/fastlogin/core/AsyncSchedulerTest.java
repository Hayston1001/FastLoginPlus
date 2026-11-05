/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

import com.github.games647.fastlogin.core.scheduler.AbstractAsyncScheduler;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class AsyncSchedulerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncSchedulerTest.class);

    @Test
    @DisabledForJreRange(min = JRE.JAVA_21)
    void legacyScheduler() {
        AsyncScheduler scheduler = new AsyncScheduler(LOGGER, Executors.newCachedThreadPool());

        AtomicBoolean virtual = new AtomicBoolean(false);
        scheduler.runAsync(() -> setVirtual(virtual)).join();

        Assertions.assertFalse(virtual.get());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void greenThread() {
        AsyncScheduler scheduler = new AsyncScheduler(LOGGER, Executors.newCachedThreadPool());

        AtomicBoolean virtual = new AtomicBoolean(false);
        scheduler.runAsync(() -> setVirtual(virtual)).join();

        Assertions.assertTrue(virtual.get());
    }

    @Test
    void awaitQuiescenceWaitsForTheRunningTask() throws InterruptedException {
        AsyncScheduler scheduler = new AsyncScheduler(LOGGER, Executors.newCachedThreadPool());

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
    void delayedTaskRunsOnceTheDelayExpired() throws Exception {
        AsyncScheduler scheduler = new AsyncScheduler(LOGGER, Executors.newCachedThreadPool());
        try {
            CountDownLatch ran = new CountDownLatch(1);
            long startedAt = System.nanoTime();
            CompletableFuture<Void> result = scheduler.runAsyncDelayed(ran::countDown, Duration.ofMillis(250L));

            Assertions.assertTrue(ran.await(5, TimeUnit.SECONDS), "the delayed task must run");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            Assertions.assertTrue(elapsedMillis >= 200L, "the task ran after " + elapsedMillis + " ms");
            result.get(5, TimeUnit.SECONDS);
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void delayedTasksDoNotOccupyProcessingThreads() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        // the shared implementation is what the Java 8/17 variant runs on; the
        // Java 21 variant only replaces the processing pool with virtual threads
        AbstractAsyncScheduler scheduler = new AbstractAsyncScheduler(LOGGER, pool) {
        };
        try {
            for (int i = 0; i < 3; i++) {
                scheduler.runAsyncDelayed(() -> { }, Duration.ofSeconds(30L));
            }

            CountDownLatch ran = new CountDownLatch(1);
            long startedAt = System.nanoTime();
            scheduler.runAsync(ran::countDown);
            Assertions.assertTrue(ran.await(2, TimeUnit.SECONDS),
                    "pending delays must not block the single processing thread");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            Assertions.assertTrue(elapsedMillis < 1_000L, "the plain task took " + elapsedMillis + " ms");
        } finally {
            scheduler.shutdown();
            pool.shutdownNow();
        }
    }

    @Test
    void delayedTaskIsDroppedAfterShutdown() throws InterruptedException {
        AsyncScheduler scheduler = new AsyncScheduler(LOGGER, Executors.newCachedThreadPool());

        AtomicBoolean ran = new AtomicBoolean(false);
        CompletableFuture<Void> result = scheduler.runAsyncDelayed(() -> ran.set(true), Duration.ofMillis(200L));
        scheduler.shutdown();

        Assertions.assertTrue(result.isDone(), "a dropped delayed task completes without running");
        Assertions.assertTrue(scheduler.awaitQuiescence(Duration.ofSeconds(5)),
                "a task waiting out its delay is not running yet");
        Thread.sleep(300L);
        Assertions.assertFalse(ran.get(), "a task that only wakes up after shutdown must not run");
    }

    @Test
    void delayedTaskCountsAsInFlightOnlyWhenItRuns() throws InterruptedException {
        AsyncScheduler scheduler = new AsyncScheduler(LOGGER, Executors.newCachedThreadPool());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        scheduler.runAsyncDelayed(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }, Duration.ofMillis(50L));

        Assertions.assertTrue(started.await(5, TimeUnit.SECONDS), "the delayed task must start");
        Assertions.assertFalse(scheduler.awaitQuiescence(Duration.ofMillis(100L)),
                "a delayed task that is running counts as in flight");
        release.countDown();
        Assertions.assertTrue(scheduler.awaitQuiescence(Duration.ofSeconds(5)));
    }

    @Test
    void failingDelayedTaskDoesNotAffectOthers() throws Exception {
        AsyncScheduler scheduler = new AsyncScheduler(LOGGER, Executors.newCachedThreadPool());
        CountDownLatch secondRan = new CountDownLatch(1);

        CompletableFuture<Void> failing = scheduler.runAsyncDelayed(
                () -> {
                    throw new IllegalStateException("expected test failure");
                }, Duration.ofMillis(20L));
        scheduler.runAsyncDelayed(secondRan::countDown, Duration.ofMillis(40L));

        failing.get(5, TimeUnit.SECONDS);
        Assertions.assertTrue(secondRan.await(5, TimeUnit.SECONDS), "the next delayed task must still run");
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
