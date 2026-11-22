/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.hook.UltraAuthHook;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackendAuthCoreTest {
    private final FastLoginBukkit host = mock(FastLoginBukkit.class);
    private final PluginManager manager = mock(PluginManager.class);
    private final BackendAuthCore core = new BackendAuthCore(host);

    BackendAuthCoreTest() {
        when(host.getLog()).thenReturn(mock(Logger.class));
    }

    @Test
    void selfRegistrationWinsWithoutStartingInstalledAuthMe() {
        AuthPlugin<Player> external = mock(AuthPlugin.class);
        when(manager.isPluginEnabled("AuthMe")).thenReturn(true);
        core.setAuthPluginHook(external);
        assertTrue(core.select(manager, true));
        core.setAuthPluginHook(null);
        assertSame(external, core.getAuthPluginHook());
        verify(host, never()).startAuthMe();
    }

    @Test
    void onlyFirstEnabledBuiltInOwnsTheBackendUntilRestart() {
        Plugin owner = mock(Plugin.class);
        when(manager.getPlugin("UltraAuth")).thenReturn(owner);
        when(manager.isPluginEnabled("UltraAuth")).thenReturn(true);
        when(manager.isPluginEnabled("Passky")).thenReturn(true);
        assertTrue(core.select(manager, false));
        assertTrue(core.getAuthPluginHook() instanceof UltraAuthHook);
        core.setAuthPluginHook(mock(AuthPlugin.class));
        assertTrue(core.getAuthPluginHook() instanceof UltraAuthHook);
        PluginDisableEvent disabled = mock(PluginDisableEvent.class);
        when(disabled.getPlugin()).thenReturn(owner);
        core.onProviderDisabled(disabled);
        verify(host).stopAuthentication();
        verify(host, never()).startAuthMe();
    }

    @Test
    void disabledProviderIsNotProxyOnlyAndBrokenWinnerDoesNotFallThrough() {
        when(manager.getPlugin("AuthMe")).thenReturn(mock(Plugin.class));
        assertFalse(core.select(manager, true));
        when(manager.isPluginEnabled("AuthMe")).thenReturn(true);
        when(manager.isPluginEnabled("LoginSecurity")).thenReturn(true);
        BackendAuthCore nextStartup = new BackendAuthCore(host);
        assertFalse(nextStartup.select(manager, true));
        assertNull(nextStartup.getAuthPluginHook());
    }
}
