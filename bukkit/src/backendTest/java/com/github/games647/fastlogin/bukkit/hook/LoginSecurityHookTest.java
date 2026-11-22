/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.lenis0012.bukkit.loginsecurity.session.PlayerSession;
import com.lenis0012.bukkit.loginsecurity.session.SessionManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginSecurityHookTest {
    @Test
    void unresolvedAccountAndReplacementPlayerCannotConfirmAnAction() {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        when(plugin.isEnabled()).thenReturn(true);
        SessionManager sessions = mock(SessionManager.class);
        LoginSecurityHook hook = new LoginSecurityHook(plugin, sessions);
        Player old = mock(Player.class);
        when(old.isOnline()).thenReturn(true);
        PlayerSession replacement = mock(PlayerSession.class);
        when(replacement.getPlayer()).thenReturn(mock(Player.class));
        when(sessions.getPlayerSession(old)).thenReturn(replacement);
        assertThrows(IllegalStateException.class, () -> hook.inspectAccount("Alex"));
        assertFalse(hook.forceLogin(old));
        assertFalse(hook.forceRegister(old, "unused"));
        verify(replacement, never()).performAction(any());
    }
}
