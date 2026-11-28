/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Empties AuthMe's persisted proxy shared secret and refreshes the live receiver.
 *
 * <p>Pure reflection over objects handed in by the platform module. The shape
 * gate lives in {@link AuthMeShapeProbe}; this class only performs the write,
 * verifies both the persisted value and the receiver cache, and restores the
 * previous value when verification fails.
 */
public final class AuthMeProxyReceiverPin {

    private AuthMeProxyReceiverPin() {
    }

    /**
     * Pins the proxy receiver off.
     *
     * @param injector AuthMe's injector, or a stand-in in tests
     * @param settingsClass AuthMe settings class
     * @param hooksClass AuthMe hooks class holding the proxy properties
     * @param propertyBase ConfigMe property base class
     * @param receiverClass AuthMe's proxy login receiver class
     * @return true when the channel was active and the pin was verified
     * @throws ReflectiveOperationException when AuthMe's graph cannot be driven
     * @throws IllegalStateException when the readback does not show the cleared secret
     */
    public static boolean pin(Object injector, Class<?> settingsClass, Class<?> hooksClass,
                              Class<?> propertyBase, Class<?> receiverClass)
            throws ReflectiveOperationException {
        Method singleton = injector.getClass().getMethod("getSingleton", Class.class);
        Object settings = singleton.invoke(injector, settingsClass);
        Object enabled = hooksClass.getField("BUNGEECORD").get(null);
        Object secret = hooksClass.getField("PROXY_SHARED_SECRET").get(null);
        Method get = settings.getClass().getMethod("getProperty", propertyBase);
        if (!Boolean.TRUE.equals(get.invoke(settings, enabled))) {
            return false;
        }
        Object previous = get.invoke(settings, secret);
        Object receiver = singleton.invoke(injector, receiverClass);
        try {
            // Always save and refresh: retry a previous failed save or a stale receiver as well.
            settings.getClass().getMethod("setProperty", propertyBase, Object.class)
                    .invoke(settings, secret, "");
            settings.getClass().getMethod("save").invoke(settings);
            // Both PLAY and Paper CONFIGURATION perform.login paths use this receiver's HMAC check.
            receiver.getClass().getMethod("reload", settingsClass).invoke(receiver, settings);
            Field cachedSecret = receiverClass.getDeclaredField("proxySharedSecret");
            cachedSecret.setAccessible(true);
            Field cachedEnabled = receiverClass.getDeclaredField("isEnabled");
            cachedEnabled.setAccessible(true);
            if (!"".equals(get.invoke(settings, secret)) || !"".equals(cachedSecret.get(receiver))
                    || !Boolean.TRUE.equals(get.invoke(settings, enabled))
                    || !Boolean.TRUE.equals(cachedEnabled.get(receiver))) {
                throw new IllegalStateException("AuthMe proxy receiver pin readback mismatch");
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            rollback(settings, receiver, settingsClass, propertyBase, secret, previous);
            throw ex;
        }
    }

    /**
     * Restores the persisted secret and refreshes the receiver. Best effort only;
     * the caller reports the original failure.
     *
     * @param settings AuthMe settings singleton
     * @param receiver AuthMe proxy receiver
     * @param settingsClass settings class used as the reload argument
     * @param propertyBase ConfigMe property base class
     * @param secret proxy shared secret property
     * @param previous value before the pin
     */
    private static void rollback(Object settings, Object receiver, Class<?> settingsClass,
                                 Class<?> propertyBase, Object secret, Object previous) {
        if (settings == null || receiver == null || previous == null) {
            return;
        }
        try {
            settings.getClass().getMethod("setProperty", propertyBase, Object.class)
                    .invoke(settings, secret, previous);
            settings.getClass().getMethod("save").invoke(settings);
            receiver.getClass().getMethod("reload", settingsClass).invoke(receiver, settings);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // the caller reports the original failure; rollback stays best-effort
        }
    }
}
