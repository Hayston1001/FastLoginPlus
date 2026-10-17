/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.compat;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthMeProxyReceiverPinTest {
    private final Injector injector = new Injector();
    private final Logger logger = mock(Logger.class);
    private final FastLoginBukkit plugin = mock(FastLoginBukkit.class);
    private final AuthMeVersionDetector detector = mock(AuthMeVersionDetector.class);

    private boolean pin() throws ReflectiveOperationException {
        return AuthMePremiumIntegrator.pinProxyReceiver(injector, Settings.class, Hooks.class,
                Property.class, Receiver.class);
    }

    private AuthMePremiumIntegrator integrator() throws Exception {
        when(plugin.getLog()).thenReturn(logger);
        when(detector.isAuthMePresent()).thenReturn(true);
        when(detector.getVersion()).thenReturn("6.0.1-b2770");
        AuthMePremiumIntegrator result = spy(new AuthMePremiumIntegrator(plugin, detector));
        Field cachedInjector = AuthMePremiumIntegrator.class.getDeclaredField("authMeInjector");
        cachedInjector.setAccessible(true);
        cachedInjector.set(result, injector);
        doAnswer(unused -> pin()).when(result).pinProxyReceiver(injector);
        return result;
    }

    @Test
    void emptiesPersistedAndCachedSecretWhileKeepingNotifications() throws Exception {
        assertTrue(pin());
        assertEquals("", injector.settings.secret);
        assertEquals("", injector.settings.persisted);
        assertEquals("", injector.receiver.proxySharedSecret);
        assertTrue(injector.settings.enabled);
        assertTrue(injector.receiver.isEnabled);
    }

    @Test
    void repeatedPinRefreshesStaleConsumerEvenWhenSettingAlreadyEmpty() throws Exception {
        pin();
        injector.receiver.proxySharedSecret = "DO-NOT-LOG-SECRET";
        assertTrue(pin());
        assertEquals("", injector.receiver.proxySharedSecret);
        assertEquals(2, injector.receiver.reloads);
    }

    @Test
    void reloadThatRestoresSecretIsPinnedAgain() throws Exception {
        pin();
        injector.settings.secret = "DO-NOT-LOG-SECRET";
        injector.receiver.reload(injector.settings);
        assertTrue(pin());
        assertEquals("", injector.settings.persisted);
        assertEquals("", injector.receiver.proxySharedSecret);
    }

    @Test
    void disabledChannelIsUntouched() throws Exception {
        injector.settings.enabled = false;
        assertFalse(pin());
        assertEquals("DO-NOT-LOG-SECRET", injector.settings.secret);
        assertEquals(0, injector.settings.saves);
        assertEquals(0, injector.receiver.reloads);
    }

    @Test
    void receiverReadbackMismatchFails() {
        injector.receiver.ignoreReload = true;
        assertThrows(IllegalStateException.class, this::pin);
    }

    @Test
    void successfulWrapperLogsTheSsoCostWithoutSecret() throws Exception {
        assertTrue(integrator().pinProxyAutoLoginOff());
        verify(logger).warn(contains("cross-server SSO"));
        assertSanitizedLogs();
    }

    @Test
    void saveFailureIsAnErrorNotACrashAndDoesNotLeakTheCause() throws Exception {
        AuthMePremiumIntegrator result = integrator();
        injector.settings.failSave = true;
        assertFalse(result.pinProxyAutoLoginOff());
        verify(logger).error(contains("NOT guaranteed disabled"));
        verify(logger, never()).warn(anyString());
        assertSanitizedLogs();
        injector.settings.failSave = false;
        assertTrue(result.pinProxyAutoLoginOff());
        assertEquals("", injector.settings.persisted);
    }

    @Test
    void unknownVersionIsAnErrorWithoutWriting() throws Exception {
        AuthMePremiumIntegrator result = integrator();
        when(detector.getVersion()).thenReturn("6.2.0");
        assertFalse(result.pinProxyAutoLoginOff());
        verify(logger).error(contains("Unsupported"));
        assertEquals(0, injector.settings.saves);
    }

    @Test
    void absentPluginIsAnInfoSkip() throws Exception {
        AuthMePremiumIntegrator result = integrator();
        when(detector.isAuthMePresent()).thenReturn(false);
        assertTrue(result.pinProxyAutoLoginOff());
        verify(logger).info(contains("absent"));
        assertEquals(0, injector.settings.saves);
    }

    @Test
    void absentClassIsAnInfoSkipWithoutClaimingEnforcement() throws Exception {
        AuthMePremiumIntegrator result = integrator();
        doThrow(new ClassNotFoundException("DO-NOT-LOG-SECRET")).when(result).pinProxyReceiver(injector);
        assertFalse(result.pinProxyAutoLoginOff());
        verify(logger).info(contains("NOT guaranteed"));
        assertSanitizedLogs();
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
    public static final class Hooks {
        public static final Property BUNGEECORD = new Property() { };
        public static final Property PROXY_SHARED_SECRET = new Property() { };
    }
    public static final class Settings {
        boolean enabled = true;
        String secret = "DO-NOT-LOG-SECRET";
        String persisted = secret;
        boolean failSave;
        int saves;
        public Object getProperty(Property property) {
            return property == Hooks.BUNGEECORD ? enabled : secret;
        }
        public void setProperty(Property property, Object value) {
            assertSame(Hooks.PROXY_SHARED_SECRET, property);
            assertEquals("", value);
            secret = (String) value;
        }
        public void save() {
            if (failSave) { throw new IllegalStateException("DO-NOT-LOG-SECRET"); }
            persisted = secret;
            saves++;
        }
    }
    public static final class Receiver {
        private String proxySharedSecret = "DO-NOT-LOG-SECRET";
        private boolean isEnabled = true;
        boolean ignoreReload;
        int reloads;
        public void reload(Settings settings) {
            reloads++;
            if (!ignoreReload) {
                proxySharedSecret = settings.secret;
                isEnabled = settings.enabled;
            }
        }
    }
    public static final class Injector {
        final Settings settings = new Settings();
        final Receiver receiver = new Receiver();
        public Object getSingleton(Class<?> type) {
            return type == Settings.class ? settings : receiver;
        }
    }
}
