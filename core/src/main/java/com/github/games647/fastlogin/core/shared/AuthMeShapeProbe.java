/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks the member shape of AuthMe's proxy objects before FLP drives them.
 *
 * <p>A version string is a poor gate: a fork can call itself 6.0.1 while changing
 * the internals, and an equivalent build can carry a different suffix. This probe
 * reads the members FLP actually touches and requires every one of them, which is
 * the whole compatibility decision. It says nothing about login semantics, so a
 * missing member is skipped and reported rather than assumed safe.
 */
public final class AuthMeShapeProbe {

    private AuthMeShapeProbe() {
    }

    /**
     * Result of a shape probe.
     */
    public static final class Result {
        private final boolean complete;
        private final String descriptor;

        private Result(boolean complete, String descriptor) {
            this.complete = complete;
            this.descriptor = descriptor;
        }

        /** @return whether every member FLP needs was found */
        public boolean isComplete() {
            return complete;
        }

        /** @return sorted descriptor, or null when the shape is incomplete */
        public String getDescriptor() {
            return descriptor;
        }
    }

    /**
     * Probes the proxy plugin object graph without changing anything.
     *
     * @param plugin live AuthMe proxy plugin
     * @param propertiesClassName ConfigMe property holder class name
     * @param propertyBaseClassName ConfigMe property base class name
     * @return probe result; never throws
     */
    public static Result probeProxyAutoLogin(Object plugin, String propertiesClassName,
                                             String propertyBaseClassName) {
        if (plugin == null || propertiesClassName == null || propertyBaseClassName == null) {
            return new Result(false, null);
        }
        ClassLoader loader = plugin.getClass().getClassLoader();
        try {
            Class<?> properties = Class.forName(propertiesClassName, false, loader);
            Class<?> propertyBase = Class.forName(propertyBaseClassName, false, loader);
            Field autoLogin = properties.getField("AUTOLOGIN");
            if (!Modifier.isStatic(autoLogin.getModifiers())
                    || !propertyBase.isAssignableFrom(autoLogin.getType())) {
                return new Result(false, null);
            }

            Object manager = readField(plugin, "configManager");
            Object bridge = readField(plugin, "proxyBridge");
            if (manager == null || bridge == null) {
                return new Result(false, null);
            }
            Object settings = readField(manager, "settingsManager");
            if (settings == null) {
                return new Result(false, null);
            }

            List<String> parts = new ArrayList<>();
            Collections.addAll(parts, "field:configManager", "field:proxyBridge", "field:settingsManager",
                    "field:configuration", "field:AUTOLOGIN");
            Class<?> configurationClass = manager.getClass().getDeclaredMethod("reload").getReturnType();
            Field configurationField = findField(bridge.getClass(), "configuration");
            if (!configurationField.getType().isAssignableFrom(configurationClass)) {
                return new Result(false, null);
            }
            settings.getClass().getMethod("getProperty", propertyBase);
            settings.getClass().getMethod("setProperty", propertyBase, Object.class);
            settings.getClass().getMethod("save");
            bridge.getClass().getDeclaredMethod("reload", configurationClass);
            configurationClass.getDeclaredMethod("autoLoginEnabled");
            Collections.addAll(parts,
                    "method:getProperty(Property)",
                    "method:setProperty(Property,Object)",
                    "method:save",
                    "method:reload()",
                    "method:reload(Configuration)",
                    "method:autoLoginEnabled");
            return new Result(true, join(parts));
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException
                | NoSuchMethodException ex) {
            return new Result(false, null);
        } catch (RuntimeException | LinkageError ex) {
            return new Result(false, null);
        }
    }

    /**
     * Probes the backend proxy receiver graph without changing anything.
     *
     * @param injector AuthMe's injector, or a stand-in in tests
     * @param settingsClass AuthMe settings class
     * @param hooksClass AuthMe hooks class holding the proxy properties
     * @param propertyBase ConfigMe property base class
     * @param receiverClass AuthMe's proxy login receiver class
     * @return probe result; never throws
     */
    public static Result probeProxyReceiver(Object injector, Class<?> settingsClass, Class<?> hooksClass,
                                            Class<?> propertyBase, Class<?> receiverClass) {
        if (injector == null || settingsClass == null || hooksClass == null || propertyBase == null
                || receiverClass == null) {
            return new Result(false, null);
        }
        try {
            Method singleton = injector.getClass().getMethod("getSingleton", Class.class);
            Object settings = singleton.invoke(injector, settingsClass);
            Object receiver = singleton.invoke(injector, receiverClass);
            if (settings == null || receiver == null) {
                return new Result(false, null);
            }
            Field bungee = hooksClass.getField("BUNGEECORD");
            Field secret = hooksClass.getField("PROXY_SHARED_SECRET");
            if (!Modifier.isStatic(bungee.getModifiers()) || !propertyBase.isAssignableFrom(bungee.getType())
                    || !Modifier.isStatic(secret.getModifiers()) || !propertyBase.isAssignableFrom(secret.getType())) {
                return new Result(false, null);
            }
            Field cachedSecret = receiverClass.getDeclaredField("proxySharedSecret");
            Field cachedEnabled = receiverClass.getDeclaredField("isEnabled");
            if (cachedSecret.getType() != String.class
                    || !(cachedEnabled.getType() == boolean.class || cachedEnabled.getType() == Boolean.class)) {
                return new Result(false, null);
            }

            List<String> parts = new ArrayList<>();
            Collections.addAll(parts, "field:BUNGEECORD", "field:PROXY_SHARED_SECRET");
            settings.getClass().getMethod("getProperty", propertyBase);
            settings.getClass().getMethod("setProperty", propertyBase, Object.class);
            settings.getClass().getMethod("save");
            receiver.getClass().getMethod("reload", settingsClass);
            Collections.addAll(parts,
                    "field:proxySharedSecret:String",
                    "field:isEnabled",
                    "method:getSingleton(Class)",
                    "method:getProperty(Property)",
                    "method:setProperty(Property,Object)",
                    "method:save",
                    "method:reload(Settings)");
            return new Result(true, join(parts));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return new Result(false, null);
        }
    }

    /**
     * Whether a probe result may be driven.
     *
     * <p>A complete shape is approved; there is no version ceiling. A missing
     * member is the caller's signal to skip and warn.
     *
     * @param shape probe result
     * @return true when every member FLP needs was found
     */
    public static boolean isApproved(Result shape) {
        return shape != null && shape.isComplete();
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String join(List<String> parts) {
        List<String> sorted = new ArrayList<>(parts);
        Collections.sort(sorted);
        return String.join(";", sorted);
    }

    private static Object readField(Object target, String name) throws IllegalAccessException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        return null;
    }
}
