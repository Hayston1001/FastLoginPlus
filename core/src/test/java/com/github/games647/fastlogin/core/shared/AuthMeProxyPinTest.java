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

import org.junit.jupiter.api.Test;

import static com.github.games647.fastlogin.core.shared.AuthMeProxyPin.Outcome.ALREADY_OFF;
import static com.github.games647.fastlogin.core.shared.AuthMeProxyPin.Outcome.APPLIED;
import static com.github.games647.fastlogin.core.shared.AuthMeProxyPin.Outcome.FAILED;
import static com.github.games647.fastlogin.core.shared.AuthMeProxyPin.Outcome.UNSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the reflective walk over AuthMe's proxy object graph.
 *
 * <p>The fixtures below mirror the shape of {@code AuthMeBungeePlugin} /
 * {@code AuthMeVelocityPlugin} as of AuthMe 6.0.1: the plugin holds a
 * {@code configManager} and a {@code proxyBridge}; the config manager holds a
 * ConfigMe {@code settingsManager} and exposes {@code reload()}; the bridge
 * exposes {@code reload(configuration)}. ConfigMe itself is not on this
 * module's classpath, so the property interface is supplied as a fixture too.
 */
class AuthMeProxyPinTest {

    private static final String PROPERTIES_CLASS = FakeConfigProperties.class.getName();
    private static final String PROPERTY_BASE_CLASS = FakeProperty.class.getName();

    @Test
    void reportsAlreadyOffWithoutTouchingAnything() {
        FakePlugin plugin = new FakePlugin(false);

        assertEquals(ALREADY_OFF, pin(plugin));
        assertTrue(!plugin.configManager.settingsManager.saved,
                "an already-off value must not be persisted");
        assertTrue(!plugin.proxyBridge.reloaded, "an already-off value must not trigger a reload");
    }

    @Test
    void flipsTheValuePersistsAndReloadsWhenOn() {
        FakePlugin plugin = new FakePlugin(true);

        assertEquals(APPLIED, pin(plugin));

        assertTrue(!plugin.configManager.settingsManager.keepOfflineUuidCompatibility,
                "the live ConfigMe value must be flipped to false");
        assertTrue(plugin.configManager.settingsManager.saved, "AuthMe must persist the new value itself");
        assertTrue(plugin.proxyBridge.reloaded,
                "the bridge must be handed a rebuilt configuration");
        assertTrue(!plugin.proxyBridge.configuration.keepOfflineUuidCompatibility,
                "the rebuilt configuration must carry the pinned value");
    }

    @Test
    void reportsUnsupportedWhenThePluginShapeChanged() {
        // an upstream rename of configManager/proxyBridge must degrade, not throw
        assertEquals(UNSUPPORTED, pin(new Object()));
    }

    @Test
    void reportsUnsupportedWhenTheSettingsManagerIsMissing() {
        FakePlugin plugin = new FakePlugin(true);
        plugin.configManager = new FakeConfigManagerWithoutSettings();

        assertEquals(UNSUPPORTED, pin(plugin));
    }

    @Test
    void reportsUnsupportedWhenThePropertiesClassIsGone() {
        FakePlugin plugin = new FakePlugin(true);

        assertEquals(UNSUPPORTED,
                AuthMeProxyPin.forceOfflineUuidCompatibilityOff(plugin, "does.not.Exist",
                        PROPERTY_BASE_CLASS));
    }

    @Test
    void reportsFailedWhenAReflectiveCallThrows() {
        FakePlugin plugin = new FakePlugin(true);
        plugin.configManager.settingsManager.failOnSave = true;

        assertEquals(FAILED, pin(plugin));
    }

    @Test
    void reportsUnsupportedForNullArguments() {
        assertEquals(UNSUPPORTED, AuthMeProxyPin.forceOfflineUuidCompatibilityOff(null, PROPERTIES_CLASS));
        assertEquals(UNSUPPORTED,
                AuthMeProxyPin.forceOfflineUuidCompatibilityOff(new FakePlugin(true), null));
    }

    private static AuthMeProxyPin.Outcome pin(Object plugin) {
        return AuthMeProxyPin.forceOfflineUuidCompatibilityOff(
                plugin, PROPERTIES_CLASS, PROPERTY_BASE_CLASS);
    }

    /** Stand-in for ConfigMe's {@code ch.jalu.configme.properties.Property}. */
    interface FakeProperty {
    }

    /** Stand-in for AuthMe's {@code BungeeConfigProperties} / {@code VelocityConfigProperties}. */
    static final class FakeConfigProperties {
        public static final FakeBooleanProperty PREMIUM_KEEP_OFFLINE_UUID_COMPATIBILITY =
                new FakeBooleanProperty();
    }

    /** Minimal typed property marker. */
    static final class FakeBooleanProperty implements FakeProperty {
    }

    /** Mirrors {@code AuthMeBungeePlugin} / {@code AuthMeVelocityPlugin}. */
    static final class FakePlugin {
        // both are non-final in AuthMe 6.0.1
        private FakeConfigManager configManager;
        private final FakeBridge proxyBridge = new FakeBridge();

        FakePlugin(boolean initialValue) {
            this.configManager = new FakeConfigManager(initialValue);
        }
    }

    /** Mirrors {@code BungeeConfigManager} / {@code VelocityConfigManager}. */
    static class FakeConfigManager {
        private final FakeSettingsManager settingsManager;

        FakeConfigManager(boolean initialValue) {
            this.settingsManager = new FakeSettingsManager(initialValue);
        }

        // package-private in AuthMe
        FakeConfiguration reload() {
            return new FakeConfiguration(settingsManager.keepOfflineUuidCompatibility);
        }
    }

    /** A config manager whose ConfigMe settings object cannot be resolved. */
    static final class FakeConfigManagerWithoutSettings extends FakeConfigManager {
        FakeConfigManagerWithoutSettings() {
            super(true);
        }
    }

    /** Mirrors ConfigMe's {@code SettingsManager} surface used by AuthMeProxyPin. */
    static final class FakeSettingsManager {
        private boolean keepOfflineUuidCompatibility;
        private boolean saved;
        private boolean failOnSave;

        FakeSettingsManager(boolean initialValue) {
            this.keepOfflineUuidCompatibility = initialValue;
        }

        public Object getProperty(FakeProperty property) {
            return keepOfflineUuidCompatibility;
        }

        public void setProperty(FakeProperty property, Object value) {
            this.keepOfflineUuidCompatibility = (Boolean) value;
        }

        public void save() {
            if (failOnSave) {
                throw new IllegalStateException("simulated AuthMe save failure");
            }
            this.saved = true;
        }
    }

    /** Mirrors {@code BungeeProxyConfiguration} / {@code VelocityProxyConfiguration}. */
    static final class FakeConfiguration {
        private final boolean keepOfflineUuidCompatibility;

        FakeConfiguration(boolean keepOfflineUuidCompatibility) {
            this.keepOfflineUuidCompatibility = keepOfflineUuidCompatibility;
        }
    }

    /** Mirrors {@code BungeeProxyBridge} / {@code VelocityProxyBridge}. */
    static final class FakeBridge {
        private FakeConfiguration configuration = new FakeConfiguration(true);
        private boolean reloaded;

        // package-private in AuthMe
        void reload(FakeConfiguration configuration) {
            this.configuration = configuration;
            this.reloaded = true;
        }
    }
}
