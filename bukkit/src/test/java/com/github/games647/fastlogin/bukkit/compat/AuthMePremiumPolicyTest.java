/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.compat;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthMePremiumPolicyTest {
    @Test
    void proxyStartupKeepsUnknownAndRemovesPacketsWithoutForcingOrReassertingAFlag() {
        AuthMeVersionDetector detector = mock(AuthMeVersionDetector.class);
        when(detector.isAuthMe6()).thenReturn(true);
        AuthMePremiumIntegrator integrator = spy(new AuthMePremiumIntegrator(mock(FastLoginBukkit.class), detector));
        doReturn(true).when(integrator).unregisterPremiumPacketListener();
        doReturn(true).when(integrator).setPremiumEnabled(anyBoolean(), anyBoolean());
        assertTrue(integrator.enforceFlpPremiumControl(true));
        assertFalse(integrator.hasProxyAutoLoginPolicy());
        assertTrue(integrator.reassertProxyAutoLogin());
        verify(integrator).unregisterPremiumPacketListener();
        verify(integrator, never()).forceEnablePremium();
        verify(integrator, never()).setPremiumEnabled(anyBoolean(), anyBoolean());
    }

    @Test
    void disabledPolicyAndReloadRequestFalseThroughTheExistingSettingMechanism() {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        AuthMePremiumIntegrator integrator = spy(new AuthMePremiumIntegrator(plugin, mock(AuthMeVersionDetector.class)));
        doReturn(true).when(integrator).setPremiumEnabled(false, false);
        doReturn(true).when(integrator).setPremiumEnabled(false, true);
        assertTrue(integrator.applyProxyAutoLogin(false));
        assertTrue(integrator.hasProxyAutoLoginPolicy());
        assertTrue(integrator.reassertProxyAutoLogin());
        verify(integrator).setPremiumEnabled(false, false);
        verify(integrator).setPremiumEnabled(false, true);
        verify(integrator, never()).setPremiumEnabled(true, true);
    }

    @Test
    void declaredOnAndReloadRequestTrueWithoutInventingOff() {
        AuthMePremiumIntegrator integrator = spy(new AuthMePremiumIntegrator(
                mock(FastLoginBukkit.class), mock(AuthMeVersionDetector.class)));
        doReturn(true).when(integrator).setPremiumEnabled(true, false);
        doReturn(true).when(integrator).setPremiumEnabled(true, true);
        assertFalse(integrator.hasProxyAutoLoginPolicy());
        assertTrue(integrator.applyProxyAutoLogin(true));
        assertTrue(integrator.hasProxyAutoLoginPolicy());
        assertTrue(integrator.reassertProxyAutoLogin());
        verify(integrator).setPremiumEnabled(true, false);
        verify(integrator).setPremiumEnabled(true, true);
        verify(integrator, never()).setPremiumEnabled(eq(false), anyBoolean());
    }

    @Test
    void standaloneStartupStillForcesTrueAndRemovesPackets() {
        AuthMeVersionDetector detector = mock(AuthMeVersionDetector.class);
        when(detector.isAuthMe6()).thenReturn(true);
        AuthMePremiumIntegrator integrator = spy(new AuthMePremiumIntegrator(mock(FastLoginBukkit.class), detector));
        doReturn(true).when(integrator).setPremiumEnabled(true, false);
        doReturn(true).when(integrator).unregisterPremiumPacketListener();
        assertTrue(integrator.enforceFlpPremiumControl(false));
        verify(integrator).forceEnablePremium();
        verify(integrator).setPremiumEnabled(true, false);
        verify(integrator).unregisterPremiumPacketListener();
    }

    @Test
    void mismatchWarningIsOnlyForDisablingAnEnabledBackendAndIsDeduplicated() {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLog()).thenReturn(logger);
        AuthMePremiumIntegrator integrator = new AuthMePremiumIntegrator(plugin, mock(AuthMeVersionDetector.class));
        integrator.warnIfPolicyDisabled(false, false);
        integrator.warnIfPolicyDisabled(false, true);
        integrator.warnIfPolicyDisabled(true, true);
        integrator.warnIfPolicyDisabled(true, false); // UNKNOWN is not an OFF declaration.
        verifyNoInteractions(logger);
        integrator.applyProxyAutoLogin(true);
        integrator.warnIfPolicyDisabled(true, false); // ON cannot consume the OFF warning budget either.
        verifyNoInteractions(logger);
        integrator.applyProxyAutoLogin(false);
        integrator.warnIfPolicyDisabled(true, false);
        integrator.applyProxyAutoLogin(false);
        integrator.warnIfPolicyDisabled(true, false);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(message.capture());
        assertTrue(message.getValue().contains("Proxy declared autoLogin=false"));
        assertTrue(message.getValue().contains("AuthMe enablePremium was true"));
        assertTrue(message.getValue().contains("synchronizing AuthMe premium mode to false"));
        verifyNoMoreInteractions(logger);
    }
}
