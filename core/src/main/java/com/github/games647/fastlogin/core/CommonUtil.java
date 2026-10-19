/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.jul.JDK14LoggerAdapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public final class CommonUtil {

    public static <K, V> ConcurrentMap<K, V> buildCache(Duration expireAfterWrite, int maxSize) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();

        if (expireAfterWrite != null) {
            builder.expireAfterWrite(expireAfterWrite);
        }

        if (maxSize > 0) {
            builder.maximumSize(maxSize);
        }

        return builder.<K, V>build().asMap();
    }

    /**
     * This creates a SLF4J logger. In the process it initializes the SLF4J service provider. This method looks
     * for the provider in the plugin jar instead of in the server jar when creating a Logger. The provider is only
     * initialized once, so this method should be called early.
     * <p>
     * The provider is bound to the service class `SLF4JServiceProvider`. Relocating this class makes it available
     * for exclusive own usage. Other dependencies will use the relocated service too, and therefore will find the
     * initialized provider.
     *
     * @param parent JDK logger
     * @return slf4j logger
     */
    public static Logger initializeLoggerService(java.util.logging.Logger parent) {
        // set the class loader to the plugin one to find our own SLF4J provider in the plugin jar and not in the global
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();

        ClassLoader pluginLoader = CommonUtil.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(pluginLoader);

        // Trigger provider search
        LoggerFactory.getLogger(parent.getName()).info("Initialize logging service");
        try {
            parent.setLevel(Level.ALL);
            return createJDKLogger(parent);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException
                 | NoSuchMethodException reflectEx) {
            parent.log(Level.WARNING, "Cannot create slf4j logging adapter", reflectEx);
            parent.log(Level.WARNING, "Creating logger instance manually...");
            return LoggerFactory.getLogger(parent.getName());
        } finally {
            // restore previous class loader
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }

    protected static JDK14LoggerAdapter createJDKLogger(java.util.logging.Logger parent)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<JDK14LoggerAdapter> adapterClass = JDK14LoggerAdapter.class;
        Constructor<JDK14LoggerAdapter> cons = adapterClass.getDeclaredConstructor(java.util.logging.Logger.class);
        cons.setAccessible(true);
        return cons.newInstance(parent);
    }

    private CommonUtil() {
        throw new RuntimeException("No instantiation of utility classes allowed");
    }
}
