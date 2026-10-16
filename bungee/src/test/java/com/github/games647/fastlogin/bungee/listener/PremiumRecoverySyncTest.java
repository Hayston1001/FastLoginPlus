/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PremiumRecoverySyncTest {
    @Test
    void verifiedBackendConfirmationRepairsProxyConsentExactlyOnce() throws Exception {
        assertConfirmation(true);
    }

    @Test
    void offlineConnectionCannotConfirmPremiumOwnership() throws Exception {
        assertConfirmation(false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertConfirmation(boolean verified) throws Exception {
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        FastLoginCore core = mock(FastLoginCore.class);
        SQLStorage storage = mock(SQLStorage.class);
        when(plugin.getName()).thenReturn("fastloginplus");
        when(plugin.getCore()).thenReturn(core);
        when(core.getStorage()).thenReturn(storage);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(storage).withNameLock(anyString(), any(Runnable.class));
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        PendingConnection connection = mock(PendingConnection.class);
        when(player.getPendingConnection()).thenReturn(connection);
        when(connection.isOnlineMode()).thenReturn(verified);
        when(player.getName()).thenReturn("TestUser");
        StoredProfile profile = new StoredProfile(UUID.randomUUID(), "TestUser", false,
                FloodgateState.FALSE, "127.0.0.1");
        profile.setSameNameCracked(true);
        BungeeLoginSession session = new BungeeLoginSession("TestUser", true, profile);
        ConcurrentHashMap<PendingConnection, BungeeLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put(connection, session);
        when(plugin.getSession()).thenReturn(sessions);
        PluginMessageListener listener = new PluginMessageListener(plugin);
        Method success = PluginMessageListener.class.getDeclaredMethod("onSuccessMessage", ProxiedPlayer.class);
        success.setAccessible(true);
        success.invoke(listener, player);
        success.invoke(listener, player);
        if (verified) {
            assertTrue(profile.isOnlinemodePreferred());
            assertFalse(profile.isSameNameCracked());
            assertTrue(session.isAlreadySaved());
            verify(storage).save(profile);
        } else {
            assertFalse(profile.isOnlinemodePreferred());
            assertTrue(profile.isSameNameCracked());
            assertFalse(session.isAlreadySaved());
            verify(storage, never()).save(any());
        }
    }
}
