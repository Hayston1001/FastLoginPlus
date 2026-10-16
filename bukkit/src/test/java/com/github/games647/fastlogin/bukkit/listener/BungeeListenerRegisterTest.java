/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.BungeeManager;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeListenerRegisterTest {
    @Test
    void existingPremiumAccountStillQueuesConfirmationForALateRegisterMessage() throws Exception {
        assertRegisterHandling(AccountState.PREMIUM_OWNED);
    }

    @Test
    void existingPasswordAccountKeepsItsPreviousRegisterMessageHandling() throws Exception {
        assertRegisterHandling(AccountState.PASSWORD_ACCOUNT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertRegisterHandling(AccountState account) throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        FastLoginCore core = mock(FastLoginCore.class);
        AuthPlugin hook = mock(AuthPlugin.class);
        BungeeManager manager = mock(BungeeManager.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getBungeeManager()).thenReturn(manager);
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(hook.isRegistered("TestUser")).thenReturn(true);
        when(hook.inspectAccount("TestUser")).thenReturn(account);
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        when(player.spigot()).thenReturn(spigot);
        when(spigot.getRawAddress()).thenReturn(address);
        UUID verified = UUID.randomUUID();
        LoginActionMessage message = new LoginActionMessage(LoginActionMessage.Type.REGISTER,
                "TestUser", UUID.randomUUID(), verified);
        message.setFacts(new IdentityClaim.Facts(true, false, IdentityClaim.Consent.NO, true, true, false));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            Method register = BungeeListener.class.getDeclaredMethod("onRegisterMessage",
                    Player.class, String.class, LoginActionMessage.class);
            register.setAccessible(true);
            register.invoke(new BungeeListener(plugin), player, "TestUser", message);
        }
        if (account == AccountState.PREMIUM_OWNED) {
            ArgumentCaptor<BukkitLoginSession> session = ArgumentCaptor.forClass(BukkitLoginSession.class);
            verify(plugin).putSession(eq(address), session.capture());
            assertTrue(session.getValue().isVerifiedPremium());
            assertFalse(session.getValue().needsRegistration());
            assertEquals(verified, session.getValue().getUuid());
            assertEquals(IdentityClaim.Consent.NO, session.getValue().getConsent());
        } else {
            verify(plugin, never()).putSession(any(), any());
        }
    }
}
