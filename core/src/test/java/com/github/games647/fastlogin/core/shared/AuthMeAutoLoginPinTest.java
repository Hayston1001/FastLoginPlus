/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthMeAutoLoginPinTest {
    private final Logger logger = mock(Logger.class);
    private final FakePlugin plugin = new FakePlugin();

    private AuthMeProxyPin.Outcome pin() {
        return AuthMeProxyPin.forceAutoLoginOff(plugin, Properties.class.getName(), "6.0.1-b2770",
                logger, Property.class.getName());
    }

    @Test
    void pinsPersistsRefreshesAndReadsBackTheActualBridge() {
        assertEquals(AuthMeProxyPin.Outcome.APPLIED, pin());
        assertFalse(plugin.configManager.settingsManager.value);
        assertFalse(plugin.configManager.settingsManager.persisted);
        assertFalse(plugin.proxyBridge.configuration.autoLoginEnabled());
        verify(logger).info(contains("including cross-server SSO"));
    }

    @Test
    void alreadyFalseStillRefreshesAStaleConsumer() {
        plugin.configManager.settingsManager.value = false;
        assertEquals(AuthMeProxyPin.Outcome.ALREADY_OFF, pin());
        assertFalse(plugin.proxyBridge.configuration.autoLoginEnabled());
    }

    @Test
    void reloadAndRepeatedPinsNeverTurnItBackOn() {
        pin();
        plugin.configManager.settingsManager.value = true;
        plugin.proxyBridge.configuration = new Configuration(true);
        assertEquals(AuthMeProxyPin.Outcome.APPLIED, pin());
        assertEquals(AuthMeProxyPin.Outcome.ALREADY_OFF, pin());
        assertFalse(plugin.proxyBridge.configuration.autoLoginEnabled());
        assertEquals(3, plugin.configManager.settingsManager.writes);
    }

    @Test
    void saveFailureDoesNotEscapeOrLeakItsSecretMessage() {
        plugin.configManager.settingsManager.failSave = true;
        assertEquals(AuthMeProxyPin.Outcome.FAILED, pin());
        verify(logger).error(contains("NOT guaranteed disabled"));
        assertSanitizedLogs();
    }

    @Test
    void failedSaveIsRetriedEvenIfMemoryAlreadyChanged() {
        plugin.configManager.settingsManager.failSave = true;
        pin();
        plugin.configManager.settingsManager.failSave = false;
        assertEquals(AuthMeProxyPin.Outcome.ALREADY_OFF, pin());
        assertFalse(plugin.configManager.settingsManager.persisted);
        assertFalse(plugin.proxyBridge.configuration.autoLoginEnabled());
    }

    @Test
    void staleBridgeReadbackIsAnErrorNotASuccess() {
        plugin.proxyBridge.ignoreReload = true;
        assertEquals(AuthMeProxyPin.Outcome.FAILED, pin());
        verify(logger).error(contains("NOT guaranteed disabled"));
        verify(logger, never()).info(contains("is disabled"));
    }

    @Test
    void settingsReadbackMismatchIsAnError() {
        plugin.configManager.reloadTrue = true;
        assertEquals(AuthMeProxyPin.Outcome.FAILED, pin());
        verify(logger).error(anyString());
    }

    @Test
    void absentPluginIsAnInfoSkip() {
        assertEquals(AuthMeProxyPin.Outcome.UNSUPPORTED,
                AuthMeProxyPin.forceAutoLoginOff(null, "absent", "", logger));
        verify(logger).info(contains("absent"));
        verify(logger, never()).error(anyString());
    }

    @Test
    void absentClassIsAnInfoSkipWithoutClaimingEnforcement() {
        assertEquals(AuthMeProxyPin.Outcome.UNSUPPORTED, AuthMeProxyPin.forceAutoLoginOff(plugin,
                "absent.Class", "6.0.1", logger, Property.class.getName()));
        verify(logger).info(contains("NOT guaranteed"));
    }

    @Test
    void unknownVersionIsAnErrorAndDoesNotWrite() {
        assertEquals(AuthMeProxyPin.Outcome.UNSUPPORTED, AuthMeProxyPin.forceAutoLoginOff(plugin,
                Properties.class.getName(), "6.1.0", logger, Property.class.getName()));
        verify(logger).error(contains("Unsupported"));
        assertEquals(0, plugin.configManager.settingsManager.writes);
    }

    @Test
    void incompatibleShapeIsAnErrorWithoutThrowing() {
        assertEquals(AuthMeProxyPin.Outcome.FAILED, AuthMeProxyPin.forceAutoLoginOff(new Properties(),
                Properties.class.getName(), "6.0.1", logger, Property.class.getName()));
        verify(logger).error(anyString());
    }

    @Test
    void onlyReviewedVersionAcceptsBuildSuffixes() {
        assertTrue(AuthMeProxyPin.isReviewedVersion("6.0.1"));
        assertTrue(AuthMeProxyPin.isReviewedVersion("6.0.1-b2770"));
        assertFalse(AuthMeProxyPin.isReviewedVersion("6.0.10"));
        assertFalse(AuthMeProxyPin.isReviewedVersion("6.0.0"));
        assertFalse(AuthMeProxyPin.isReviewedVersion(null));
    }

    private void assertSanitizedLogs() {
        mockingDetails(logger).getInvocations().forEach(invocation -> {
            for (Object arg : invocation.getArguments()) {
                assertFalse(arg instanceof Throwable);
                assertFalse(String.valueOf(arg).contains("DO-NOT-LOG-SECRET"));
            }
        });
    }

    interface Property { }
    public static final class Properties {
        public static final Property AUTOLOGIN = new Property() { };
    }
    public static final class FakePlugin {
        private final Manager configManager = new Manager();
        private final Bridge proxyBridge = new Bridge();
    }
    public static final class Settings {
        boolean value = true;
        boolean persisted = true;
        boolean failSave;
        int writes;
        public Object getProperty(Property property) { return value; }
        public void setProperty(Property property, Object value) {
            assertEquals(Boolean.FALSE, value);
            this.value = (Boolean) value;
            writes++;
        }
        public void save() {
            if (failSave) { throw new IllegalStateException("DO-NOT-LOG-SECRET"); }
            persisted = value;
        }
    }
    public static final class Manager {
        private final Settings settingsManager = new Settings();
        boolean reloadTrue;
        Configuration reload() {
            if (reloadTrue) { settingsManager.value = true; }
            return new Configuration(settingsManager.persisted);
        }
    }
    public static final class Configuration {
        final boolean autoLogin;
        Configuration(boolean enabled) { autoLogin = enabled; }
        boolean autoLoginEnabled() { return autoLogin; }
    }
    public static final class Bridge {
        private Configuration configuration = new Configuration(true);
        boolean ignoreReload;
        void reload(Configuration configuration) {
            if (!ignoreReload) { this.configuration = configuration; }
        }
    }
}
