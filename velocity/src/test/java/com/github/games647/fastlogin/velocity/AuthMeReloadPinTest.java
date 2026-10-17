/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity;

import com.velocitypowered.api.event.command.PostCommandInvocationEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthMeReloadPinTest {
    @Test
    void matchesActualReloadCommandOnly() {
        assertTrue(FastLoginVelocity.isAuthMeReloadCommand("/AVRELOADPROXY"));
        assertTrue(FastLoginVelocity.isAuthMeReloadCommand(" avreloadproxy "));
        assertFalse(FastLoginVelocity.isAuthMeReloadCommand("avreloadproxy-other"));
        assertFalse(FastLoginVelocity.isAuthMeReloadCommand("flp reload"));
        assertFalse(FastLoginVelocity.isAuthMeReloadCommand(null));
        assertFalse(FastLoginVelocity.isAuthMeReloadCommand(""));
    }

    @Test
    void postInvocationReassertsThePinWithoutReadingFlpPolicy() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        doCallRealMethod().when(plugin).onAuthMeReload(any());
        PostCommandInvocationEvent event = mock(PostCommandInvocationEvent.class);
        when(event.getCommand()).thenReturn("avreloadproxy");
        plugin.onAuthMeReload(event);
        verify(plugin).reassertAuthMeAutoLoginPin();
        verify(plugin, never()).getCore();
    }

    @Test
    void otherCommandsDoNotReassertThePin() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        doCallRealMethod().when(plugin).onAuthMeReload(any());
        PostCommandInvocationEvent event = mock(PostCommandInvocationEvent.class);
        when(event.getCommand()).thenReturn("other");
        plugin.onAuthMeReload(event);
        verify(plugin, never()).reassertAuthMeAutoLoginPin();
    }
}
