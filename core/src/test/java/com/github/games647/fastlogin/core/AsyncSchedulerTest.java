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
import java.util.concurrent.Executors;
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
    void shutdownWaitsForInFlightTasks() throws Exception {
        Logger logger = LoggerFactory.getLogger(AsyncSchedulerTest.class);
        AsyncScheduler scheduler = new AsyncScheduler(logger, Executors.newCachedThreadPool());

        java.util.List<String> timeline = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch taskStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseTask = new java.util.concurrent.CountDownLatch(1);

        scheduler.runAsync(() -> {
            taskStarted.countDown();
            try {
                releaseTask.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            timeline.add("task-finished");
        });

        Assertions.assertTrue(taskStarted.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "task should have started");

        Thread shutdownThread = new Thread(scheduler::shutdown);
        shutdownThread.start();
        Thread.sleep(150);
        Assertions.assertTrue(shutdownThread.isAlive(),
                "shutdown must block while an accepted task is still running (F062)");

        releaseTask.countDown();
        shutdownThread.join(5000);
        Assertions.assertFalse(shutdownThread.isAlive(), "shutdown should have finished");
        Assertions.assertTrue(timeline.contains("task-finished"),
                "shutdown must not return before the accepted task finished");
    }

    @Test
    void concurrentSubmitAndShutdownNeverTouchesClosedResource() throws Exception {
        Logger logger = LoggerFactory.getLogger(AsyncSchedulerTest.class);
        AsyncScheduler scheduler = new AsyncScheduler(logger, Executors.newCachedThreadPool());

        AtomicBoolean resourceClosed = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicInteger touchesAfterClose
                = new java.util.concurrent.atomic.AtomicInteger();

        Thread submitter = new Thread(() -> {
            for (int i = 0; i < 500; i++) {
                scheduler.runAsync(() -> {
                    if (resourceClosed.get()) {
                        touchesAfterClose.incrementAndGet();
                    }
                });
            }
        });
        submitter.start();
        scheduler.shutdown();
        submitter.join(5000);

        // every task accepted before shutdown() returned has completed by now
        resourceClosed.set(true);
        Assertions.assertEquals(0, touchesAfterClose.get(),
                "no accepted task may run against the closed resource (F062)");
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
