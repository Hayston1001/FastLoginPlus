/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginMethodDecider;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.connection.PlayerConnection;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginDeliveryTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void configureRejectionDisconnectsTheConnectionOnEveryAttempt() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        FastLoginCore core = mock(FastLoginCore.class);
        AuthPlugin hook = mock(AuthPlugin.class);
        set(plugin, "core", core);
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(core.getMessage("ownership-conflict")).thenReturn("\u00a74ownership-conflict");
        when(hook.inspectAccount("TestUser")).thenReturn(LoginMethodDecider.AccountState.PREMIUM_OWNED);
        PlayerConnection connection = mock(PlayerConnection.class);
        Method decide = FastLoginBukkit.class.getDeclaredMethod("allowsPremiumLogin", String.class,
                IdentityClaim.Facts.class, Object.class, InetSocketAddress.class);
        decide.setAccessible(true);
        IdentityClaim.Facts facts = new IdentityClaim.Facts(false, false, IdentityClaim.Consent.NO,
                true, true, false);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertFalse((Boolean) decide.invoke(plugin, "TestUser", facts, connection,
                    new InetSocketAddress("127.0.0.1", 12345)));
        }
        verify(connection, times(2)).disconnect(
                LegacyComponentSerializer.legacySection().deserialize("\u00a74ownership-conflict"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void configureNoticeIsDeferredAndDeduplicatedAgainstLateDelivery() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        FastLoginCore core = mock(FastLoginCore.class);
        AuthPlugin hook = mock(AuthPlugin.class);
        set(plugin, "core", core);
        set(plugin, "logger", mock(Logger.class));
        set(plugin, "pendingKicks", new ConcurrentHashMap<String, String>());
        Cache<InetSocketAddress, Boolean> notices = CacheBuilder.newBuilder().build();
        set(plugin, "passwordNotices", notices);
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(hook.inspectAccount("TestUser")).thenReturn(LoginMethodDecider.AccountState.PASSWORD_ACCOUNT);
        PlayerConnection connection = mock(PlayerConnection.class);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        Method decide = FastLoginBukkit.class.getDeclaredMethod("allowsPremiumLogin", String.class,
                IdentityClaim.Facts.class, Object.class, InetSocketAddress.class);
        decide.setAccessible(true);
        assertFalse((Boolean) decide.invoke(plugin, "TestUser", new IdentityClaim.Facts(false, false,
                IdentityClaim.Consent.NO, true, true, false), connection, address));
        verify(connection, never()).disconnect(any());
        verify(core, never()).sendLocaleMessage(eq("premium-password-login"), any());
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(spigot.getRawAddress()).thenReturn(new InetSocketAddress("127.0.0.2", 23456));
        when(player.getAddress()).thenReturn(address);
        when(player.getName()).thenReturn("TestUser");
        when(player.hasMetadata("flp-password-notice")).thenReturn(false, true, true);
        assertTrue(plugin.deliverPendingLogin(player));
        assertTrue(plugin.deliverPendingLogin(player));
        plugin.sendPasswordNotice(player);
        verify(core).sendLocaleMessage("premium-password-login", player);
        verify(player, never()).kickPlayer(any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void incompatibleConfigureConnectionQueuesRejectionAndKicksAtJoin() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        FastLoginCore core = mock(FastLoginCore.class);
        AuthPlugin hook = mock(AuthPlugin.class);
        Logger logger = mock(Logger.class);
        ConcurrentHashMap<String, String> kicks = new ConcurrentHashMap<>();
        set(plugin, "core", core);
        set(plugin, "logger", logger);
        set(plugin, "pendingKicks", kicks);
        when(core.getAuthPluginHook()).thenReturn(hook);
        String reason = "\u00a74ownership-conflict";
        when(core.getMessage("ownership-conflict")).thenReturn(reason);
        when(hook.inspectAccount("TestUser")).thenReturn(LoginMethodDecider.AccountState.PREMIUM_OWNED);
        Method decide = FastLoginBukkit.class.getDeclaredMethod("allowsPremiumLogin", String.class,
                IdentityClaim.Facts.class, Object.class, InetSocketAddress.class);
        decide.setAccessible(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertFalse((Boolean) decide.invoke(plugin, "TestUser", new IdentityClaim.Facts(false, false,
                    IdentityClaim.Consent.NO, true, true, false), new Object(),
                    new InetSocketAddress("127.0.0.1", 12345)));
            bukkit.verify(() -> Bukkit.getPlayerExact("TestUser"));
        }
        assertEquals(reason, kicks.get("TestUser"));
        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(logger).error(eq("Could not disconnect the configuration connection for {}; deferring rejection to join"),
                eq("TestUser"), failure.capture());
        assertTrue(failure.getValue() instanceof IllegalArgumentException);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestUser");
        assertFalse(plugin.deliverPendingLogin(player));
        verify(player).kickPlayer(reason);
        assertNull(plugin.takePendingKick("TestUser"));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = FastLoginBukkit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
