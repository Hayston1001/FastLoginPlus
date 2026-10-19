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
