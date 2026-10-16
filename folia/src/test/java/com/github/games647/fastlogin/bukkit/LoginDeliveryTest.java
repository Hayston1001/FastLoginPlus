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
import org.bukkit.configuration.file.FileConfiguration;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginDeliveryTest {
    @Test
    void configureRejectionDisconnectsTheConnectionOnEveryAttempt() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        PlayerConnection connection = mock(PlayerConnection.class);
        Method disconnect = FastLoginBukkit.class.getDeclaredMethod("disconnectConfiguration", String.class,
                Object.class, String.class);
        disconnect.setAccessible(true);
        assertEquals(LoginMethodDecider.Outcome.REJECT, LoginMethodDecider.decide(false,
                LoginMethodDecider.AccountState.PREMIUM_OWNED, IdentityClaim.Consent.NO));
        for (int attempt = 0; attempt < 2; attempt++) {
            disconnect.invoke(plugin, "TestUser", connection, "\u00a74ownership-conflict");
        }
        verify(connection, times(2)).disconnect(
                LegacyComponentSerializer.legacySection().deserialize("\u00a74ownership-conflict"));
    }

    @Test
    void configureNoticeIsDeferredAndDeduplicatedAgainstLateDelivery() throws Exception {
        assertConfigureNotice(IdentityClaim.Consent.NO);
    }

    @Test
    void unknownConsentConfigureNoticeIsAlsoDeliveredOnce() throws Exception {
        assertConfigureNotice(IdentityClaim.Consent.UNKNOWN);
    }

    @Test
    void configureWithoutFactsDoesNotQueuePasswordExplanation() throws Exception {
        assertConfigureNotice(null);
    }

    @Test
    void policyDisabledPremiumOwnerGetsDedicatedNoticeOnceAtJoinAndLateDelivery() throws Exception {
        for (IdentityClaim.Consent consent : IdentityClaim.Consent.values()) {
            assertConfigureNotice(consent, true);
        }
    }

    @Test
    void configureWithoutFactsDoesNotQueuePolicyExplanation() throws Exception {
        assertConfigureNotice(null, true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertConfigureNotice(IdentityClaim.Consent consent) throws Exception {
        assertConfigureNotice(consent, false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertConfigureNotice(IdentityClaim.Consent consent, boolean policyDisabled) throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        FastLoginCore core = mock(FastLoginCore.class);
        AuthPlugin hook = mock(AuthPlugin.class);
        set(plugin, "core", core);
        set(plugin, "logger", mock(Logger.class));
        set(plugin, "pendingKicks", new ConcurrentHashMap<String, String>());
        Cache<InetSocketAddress, String> notices = CacheBuilder.newBuilder().build();
        set(plugin, "passwordNotices", notices);
        FileConfiguration config = mock(FileConfiguration.class);
        doReturn(config).when(plugin).getConfig();
        when(config.getBoolean("autoLogin")).thenReturn(!policyDisabled);
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(hook.inspectAccount("TestUser")).thenReturn(policyDisabled
                ? LoginMethodDecider.AccountState.PREMIUM_OWNED : LoginMethodDecider.AccountState.PASSWORD_ACCOUNT);
        PlayerConnection connection = mock(PlayerConnection.class);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        Method decide = FastLoginBukkit.class.getDeclaredMethod("allowsPremiumLogin", String.class,
                IdentityClaim.Facts.class, Object.class, InetSocketAddress.class);
        decide.setAccessible(true);
        IdentityClaim.Facts facts = consent == null ? null
                : new IdentityClaim.Facts(false, false, consent, !policyDisabled, true, false);
        assertFalse((Boolean) decide.invoke(plugin, "TestUser", facts, connection, address));
        verify(connection, never()).disconnect(any());
        verify(core, never()).sendLocaleMessage(eq("premium-password-login"), any());
        verify(core, never()).sendLocaleMessage(eq("premium-policy-disabled"), any());
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(spigot.getRawAddress()).thenReturn(new InetSocketAddress("127.0.0.2", 23456));
        when(player.getAddress()).thenReturn(address);
        when(player.getName()).thenReturn("TestUser");
        when(player.hasMetadata("flp-password-notice")).thenReturn(false, true, true);
        when(player.hasMetadata("flp-premium-policy-notice")).thenReturn(false, true, true);
        assertTrue(plugin.deliverPendingLogin(player));
        assertTrue(plugin.deliverPendingLogin(player));
        if (facts == null) {
            verify(core, never()).sendLocaleMessage(eq("premium-password-login"), any());
            verify(core, never()).sendLocaleMessage(eq("premium-policy-disabled"), any());
        } else if (policyDisabled) {
            plugin.sendPremiumPolicyNotice(player);
            verify(core).sendLocaleMessage("premium-policy-disabled", player);
            verify(core, never()).sendLocaleMessage(eq("premium-password-login"), any());
        } else {
            plugin.sendPasswordNotice(player);
            verify(core).sendLocaleMessage("premium-password-login", player);
        }
        verify(player, never()).kickPlayer(any());
    }

    @Test
    void incompatibleConfigureConnectionQueuesRejectionAndKicksAtJoin() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        Logger logger = mock(Logger.class);
        ConcurrentHashMap<String, String> kicks = new ConcurrentHashMap<>();
        set(plugin, "logger", logger);
        set(plugin, "pendingKicks", kicks);
        String reason = "\u00a74ownership-conflict";
        Method disconnect = FastLoginBukkit.class.getDeclaredMethod("disconnectConfiguration", String.class,
                Object.class, String.class);
        disconnect.setAccessible(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            disconnect.invoke(plugin, "TestUser", new Object(), reason);
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

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void verifiedPremiumOwnedAccountIsAllowedAtConfigureWithoutConsent() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        FastLoginCore core = mock(FastLoginCore.class);
        AuthPlugin hook = mock(AuthPlugin.class);
        set(plugin, "core", core);
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(hook.inspectAccount("TestUser")).thenReturn(LoginMethodDecider.AccountState.PREMIUM_OWNED);
        PlayerConnection connection = mock(PlayerConnection.class);
        Method decide = FastLoginBukkit.class.getDeclaredMethod("allowsPremiumLogin", String.class,
                IdentityClaim.Facts.class, Object.class, InetSocketAddress.class);
        decide.setAccessible(true);
        for (IdentityClaim.Consent consent : IdentityClaim.Consent.values()) {
            assertTrue((Boolean) decide.invoke(plugin, "TestUser", new IdentityClaim.Facts(false, false,
                    consent, true, true, false), connection, new InetSocketAddress("127.0.0.1", 12345)));
        }
        verify(connection, never()).disconnect(any());
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = FastLoginBukkit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
