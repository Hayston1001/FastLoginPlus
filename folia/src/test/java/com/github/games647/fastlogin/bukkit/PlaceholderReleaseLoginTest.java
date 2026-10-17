/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;

import static com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator.ReleaseOutcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class PlaceholderReleaseLoginTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID connectionUuid = UUID.nameUUIDFromBytes(new byte[]{1, 2, 3});
    private final Object connection = new Object();
    private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
    private final Cache<InetSocketAddress, String> notices = CacheBuilder.newBuilder().build();
    private FastLoginBukkit plugin;
    private FastLoginCore core;
    private AuthPlugin hook;
    private AuthMePremiumIntegrator integrator;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        core = mock(FastLoginCore.class);
        hook = mock(AuthPlugin.class);
        integrator = mock(AuthMePremiumIntegrator.class);
        set("core", core);
        set("logger", mock(Logger.class));
        set("authMePremiumIntegrator", integrator);
        set("passwordNotices", notices);
        doReturn(mock(FileConfiguration.class)).when(plugin).getConfig();
        doNothing().when(plugin).denyLogin(anyString(), any());
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(core.getMessage("ownership-conflict")).thenReturn("ownership-conflict");
        when(hook.inspectAccount("TestUser")).thenReturn(AccountState.PREMIUM_OWNED);
    }

    @ParameterizedTest
    @EnumSource(value = AuthMePremiumIntegrator.ReleaseOutcome.class, names = {"RELEASED", "ALREADY_ABSENT"})
    void successfulReleaseSuppressesNoticeClosesBothDialogsAndDoesNotGrantLogin(
            AuthMePremiumIntegrator.ReleaseOutcome result) throws Exception {
        notices.put(address, "premium-policy-disabled");
        when(integrator.releaseUnusablePlaceholder("TestUser", owner)).thenReturn(result);
        assertFalse(decide(true, false, owner));
        verify(integrator, times(1)).releaseUnusablePlaceholder("TestUser", owner);
        assertNull(notices.getIfPresent(address));
        verify(integrator).closePreJoinRegisterDialog(connectionUuid, connection);
        verify(integrator).closePreJoinLoginDialog(connectionUuid, connection);
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(integrator, never()).injectVerifiedUuid(anyString(), any());
        verify(hook, never()).forceLogin(any());
        verify(hook, never()).forceRegister(any(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = AuthMePremiumIntegrator.ReleaseOutcome.class,
            names = {"NOT_APPLICABLE", "UNSUPPORTED", "FAILED"})
    void releaseFailureKeepsNoticeAndDoesNotCloseDialogs(AuthMePremiumIntegrator.ReleaseOutcome result)
            throws Exception {
        when(integrator.releaseUnusablePlaceholder("TestUser", owner)).thenReturn(result);
        assertFalse(decide(true, false, owner));
        assertEquals("premium-policy-disabled", notices.getIfPresent(address));
        verify(integrator, never()).closePreJoinRegisterDialog(any(), any());
        verify(integrator, never()).closePreJoinLoginDialog(any(), any());
    }

    @Test
    void missingVerifiedUuidDoesNotPermitRelease() throws Exception {
        when(integrator.releaseUnusablePlaceholder("TestUser", null)).thenReturn(NOT_APPLICABLE);
        assertFalse(decide(true, false, null));
        assertEquals("premium-policy-disabled", notices.getIfPresent(address));
    }

    @Test
    void missingIntegratorKeepsNoticeWithoutCrash() throws Exception {
        set("authMePremiumIntegrator", null);
        assertFalse(decide(true, false, owner));
        assertEquals("premium-policy-disabled", notices.getIfPresent(address));
    }

    @Test
    void offlineSameNameRejectsWithoutRelease() throws Exception {
        assertFalse(decide(false, false, owner));
        verify(plugin).denyLogin("TestUser", "ownership-conflict");
        verifyNoInteractions(integrator);
    }

    @Test
    void passwordAccountKeepsPasswordNoticeWithoutRelease() throws Exception {
        when(hook.inspectAccount("TestUser")).thenReturn(AccountState.PASSWORD_ACCOUNT);
        assertFalse(decide(true, false, owner));
        assertEquals("premium-password-login", notices.getIfPresent(address));
        verifyNoInteractions(integrator);
    }

    @Test
    void enabledPolicyRetainsAutoLoginWithoutRelease() throws Exception {
        assertTrue(decide(true, true, owner));
        verifyNoInteractions(integrator);
        assertNull(notices.getIfPresent(address));
    }

    @Test
    void accountReadFailureKeepsLockWithoutRelease() throws Exception {
        when(hook.inspectAccount("TestUser")).thenThrow(new IllegalStateException("unreadable"));
        assertFalse(decide(true, false, owner));
        verifyNoInteractions(integrator);
    }

    @Test
    void localDisabledPolicyAlsoReleasesWithoutDeclaredFacts() throws Exception {
        when(integrator.releaseUnusablePlaceholder("TestUser", owner)).thenReturn(RELEASED);
        assertFalse(invoke(true, null, owner, connection));
        verify(integrator).releaseUnusablePlaceholder("TestUser", owner);
        assertNull(notices.getIfPresent(address));
    }

    private boolean decide(boolean verified, boolean enabled, UUID verifiedUuid) throws Exception {
        return invoke(verified, new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES,
                enabled, true, false), verifiedUuid, verified ? connection : null);
    }

    private boolean invoke(boolean verified, IdentityClaim.Facts facts, UUID verifiedUuid, Object target)
            throws Exception {
        Method method = FastLoginBukkit.class.getDeclaredMethod("allowsIdentityLogin", String.class,
                boolean.class, IdentityClaim.Facts.class, Object.class, InetSocketAddress.class,
                UUID.class, UUID.class);
        method.setAccessible(true);
        return (boolean) method.invoke(plugin, "TestUser", verified, facts, target, address,
                verifiedUuid, connectionUuid);
    }

    private void set(String name, Object value) throws Exception {
        Field field = FastLoginBukkit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
