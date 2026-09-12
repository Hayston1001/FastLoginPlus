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
package com.github.games647.fastlogin.core.shared;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Pins AuthMe's proxy-side {@code premium.keepOfflineUuidCompatibility} to
 * {@code false} by driving AuthMe's own objects, mirroring what
 * {@code AuthMePremiumIntegrator.forceEnablePremium()} does for the backend's
 * {@code settings.enablePremium}.
 *
 * <p>With the flag {@code true}, AuthMe performs its own login-phase handshake
 * and resumes the login by re-injecting it into the pipeline; on BungeeCord that
 * re-injected login reaches {@code PreLoginEvent}, where FastLoginPlus turns on
 * online mode, so a second encryption layer is stacked on the same connection.
 *
 * <h2>Why reflection instead of editing the config file</h2>
 * Rewriting the file and dispatching AuthMe's reload command goes through four
 * indirections (command lookup &rarr; command body &rarr; config reload &rarr;
 * listener refresh) and BungeeCord swallows exceptions thrown inside a command
 * body while still reporting success, so the outcome is unknowable from the
 * caller. Driving the objects directly is synchronous and inspectable; it is the
 * same working approach the backend already uses. AuthMe persists the new value
 * through its own {@code save()}, so the on-disk file is updated with AuthMe's
 * formatting rather than ours.
 *
 * <p>Every reflective step is best-effort: a failure is reported through the
 * returned {@link Outcome} and never propagated, so callers can fall back to a
 * different mechanism.
 */
public final class AuthMeProxyPin {

    /**
     * Result of an attempt to pin the setting. Callers decide how to fall back
     * for {@link #UNSUPPORTED} and {@link #FAILED}.
     */
    public enum Outcome {
        /** The setting was already {@code false}; nothing was touched. */
        ALREADY_OFF,
        /** The setting was flipped off and AuthMe reloaded itself. */
        APPLIED,
        /** AuthMe's shape is not the expected one (upstream rename or refactor). */
        UNSUPPORTED,
        /** AuthMe's shape matched but a reflective call failed. */
        FAILED
    }

    private static final String CONFIG_MANAGER_FIELD = "configManager";
    private static final String BRIDGE_FIELD = "proxyBridge";
    private static final String SETTINGS_MANAGER_FIELD = "settingsManager";
    private static final String PROPERTY_FIELD = "PREMIUM_KEEP_OFFLINE_UUID_COMPATIBILITY";
    private static final String CONFIGME_PROPERTY_CLASS = "ch.jalu.configme.properties.Property";
    private static final String CONFIG_MANAGER_RELOAD = "reload";

    private AuthMeProxyPin() {
    }

    /**
     * Flips AuthMe's proxy-side offline-UUID mode off and makes AuthMe apply it.
     *
     * <p>The sequence mirrors the backend integration: read the live value from
     * AuthMe's own ConfigMe settings, write it back, persist through AuthMe's
     * {@code save()}, then call AuthMe's config reload so its in-memory
     * configuration object (and, on BungeeCord, the packet-listener
     * registration that depends on the flag) is rebuilt.
     *
     * @param plugin              the AuthMe proxy plugin instance
     * @param propertiesClassName fully qualified name of the proxy plugin's
     *                            {@code ConfigProperties} class
     * @return what happened; never throws
     */
    public static Outcome forceOfflineUuidCompatibilityOff(Object plugin, String propertiesClassName) {
        return forceOfflineUuidCompatibilityOff(plugin, propertiesClassName, CONFIGME_PROPERTY_CLASS);
    }

    /**
     * Same as {@link #forceOfflineUuidCompatibilityOff(Object, String)} with the ConfigMe
     * {@code Property} base class injectable, so the reflection walk can be tested without a
     * ConfigMe dependency on the core module's classpath.
     *
     * @param plugin                 the AuthMe proxy plugin instance
     * @param propertiesClassName    fully qualified name of the proxy plugin's properties class
     * @param propertyBaseClassName  fully qualified name of ConfigMe's {@code Property} interface
     * @return what happened; never throws
     */
    static Outcome forceOfflineUuidCompatibilityOff(Object plugin, String propertiesClassName,
                                                    String propertyBaseClassName) {
        if (plugin == null || propertiesClassName == null || propertyBaseClassName == null) {
            return Outcome.UNSUPPORTED;
        }

        ClassLoader authMeLoader = plugin.getClass().getClassLoader();
        try {
            Object configManager = readField(plugin, CONFIG_MANAGER_FIELD);
            Object bridge = readField(plugin, BRIDGE_FIELD);
            if (configManager == null || bridge == null) {
                return Outcome.UNSUPPORTED;
            }

            Object settingsManager = readField(configManager, SETTINGS_MANAGER_FIELD);
            if (settingsManager == null) {
                return Outcome.UNSUPPORTED;
            }

            Class<?> propertyBase = Class.forName(propertyBaseClassName, false, authMeLoader);
            Class<?> properties = Class.forName(propertiesClassName, false, authMeLoader);
            Object property = properties.getField(PROPERTY_FIELD).get(null);

            Method getProperty = settingsManager.getClass().getMethod("getProperty", propertyBase);
            if (Boolean.FALSE.equals(getProperty.invoke(settingsManager, property))) {
                return Outcome.ALREADY_OFF;
            }

            Method setProperty = settingsManager.getClass()
                    .getMethod("setProperty", propertyBase, Object.class);
            setProperty.invoke(settingsManager, property, false);
            settingsManager.getClass().getMethod("save").invoke(settingsManager);

            // rebuild AuthMe's immutable configuration snapshot and hand it to the bridge.
            // BungeeCord's bridge additionally re-runs premiumVerificationManager.refreshRegistration(),
            // which is what unregisters AuthMe's own PacketEvents listener.
            Method reload = configManager.getClass().getDeclaredMethod(CONFIG_MANAGER_RELOAD);
            reload.setAccessible(true);
            Object configuration = reload.invoke(configManager);

            Method applyReload = bridge.getClass()
                    .getDeclaredMethod(CONFIG_MANAGER_RELOAD, configuration.getClass());
            applyReload.setAccessible(true);
            applyReload.invoke(bridge, configuration);

            return Outcome.APPLIED;
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ex) {
            // AuthMe is a different shape than this build expects — not an error on our side
            return Outcome.UNSUPPORTED;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ex) {
            return Outcome.FAILED;
        }
    }

    /**
     * Reads a declared field, walking up to superclasses.
     *
     * @param target    the instance to read from
     * @param fieldName the field name
     * @return the field value, or null when the field does not exist or is unreadable
     * @throws IllegalAccessException if the field exists but cannot be read
     */
    private static Object readField(Object target, String fieldName) throws IllegalAccessException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        return null;
    }
}
