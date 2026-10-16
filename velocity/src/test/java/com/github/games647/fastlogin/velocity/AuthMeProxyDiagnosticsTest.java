/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthMeProxyDiagnosticsTest {
    @Test
    void injectablePresenceProbeReportsEveryStartupWithoutDependingOnPolicy() {
        Logger logger = mock(Logger.class);
        AtomicBoolean present = new AtomicBoolean(false);
        AuthMeProxyDiagnostics diagnostics = new AuthMeProxyDiagnostics();
        diagnostics.warnAtStartup(present::get, logger);
        verifyNoInteractions(logger);
        present.set(true);
        diagnostics.warnAtStartup(present::get, logger);
        diagnostics.warnAtStartup(present::get, logger);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(logger, times(2)).warn(message.capture());
        assertTrue(message.getValue().contains("Detected AuthMeVelocity"));
        assertTrue(message.getValue().contains("FLP cannot disable that proxy-side behavior"));
        assertTrue(message.getValue().contains("configure AuthMeVelocity as well"));
        verifyNoMoreInteractions(logger);
    }
}
