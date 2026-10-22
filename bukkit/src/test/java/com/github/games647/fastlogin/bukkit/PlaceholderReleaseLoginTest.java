/*
 * SPDX-License-Identifier: MIT
 *
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
import io.papermc.paper.connection.PlayerConnection;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
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
        verify(plugin, never()).denyLogin(anyString(), any());
        verify(core, never()).getMessage("ownership-conflict");
    }

    @ParameterizedTest
    @EnumSource(IdentityClaim.Consent.class)
    void ownershipMismatchClosesDialogsAndDisconnectsWithLocalizedReason(IdentityClaim.Consent consent)
            throws Exception {
        PlayerConnection target = mock(PlayerConnection.class);
        String reason = "\u00a74Localized ownership conflict";
        when(core.getMessage("ownership-conflict")).thenReturn(reason);
        when(integrator.releaseUnusablePlaceholder("TestUser", owner)).thenReturn(UUID_MISMATCH);
        notices.put(address, "premium-policy-disabled");
        assertFalse(invoke(true, new IdentityClaim.Facts(false, false, consent, false, true, false),
                owner, target));
        assertNull(notices.getIfPresent(address));
        InOrder order = inOrder(integrator, target);
        order.verify(integrator).releaseUnusablePlaceholder("TestUser", owner);
        order.verify(integrator).closePreJoinRegisterDialog(connectionUuid, target);
        order.verify(integrator).closePreJoinLoginDialog(connectionUuid, target);
        order.verify(target).disconnect(LegacyComponentSerializer.legacySection().deserialize(reason));
        verify(plugin, never()).denyLogin(anyString(), any());
        verifyNoMoreInteractions(integrator);
        verify(hook, never()).forceLogin(any());
        verify(hook, never()).forceRegister(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void localOwnershipMismatchUsesLoginRejectionWhenConfigureConnectionIsUnavailable(boolean incompatible)
            throws Exception {
        Object target = incompatible ? connection : null;
        when(integrator.releaseUnusablePlaceholder("TestUser", owner)).thenReturn(UUID_MISMATCH);
        notices.put(address, "premium-policy-disabled");
        assertFalse(invoke(true, null, owner, target));
        assertNull(notices.getIfPresent(address));
        InOrder order = inOrder(integrator, plugin);
        order.verify(integrator).closePreJoinRegisterDialog(connectionUuid, target);
        order.verify(integrator).closePreJoinLoginDialog(connectionUuid, target);
        order.verify(plugin).denyLogin("TestUser", "ownership-conflict");
    }

    @Test
    void missingVerifiedUuidDoesNotPermitRelease() throws Exception {
        when(integrator.releaseUnusablePlaceholder("TestUser", null)).thenReturn(NOT_APPLICABLE);
        assertFalse(decide(true, false, null));
        assertEquals("premium-policy-disabled", notices.getIfPresent(address));
        verify(plugin, never()).denyLogin(anyString(), any());
        verify(core, never()).getMessage("ownership-conflict");
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
    void verifiedPasswordAccountKeepsNoticeWhenAutoLoginIsDisabled() throws Exception {
        when(hook.inspectAccount("TestUser")).thenReturn(AccountState.PASSWORD_ACCOUNT);
        assertFalse(decide(true, false, owner));
        assertEquals("premium-password-login", notices.getIfPresent(address));
        verifyNoInteractions(integrator);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void crackedPasswordAccountDoesNotQueuePremiumNotice(boolean autoLogin) throws Exception {
        when(hook.inspectAccount("TestUser")).thenReturn(AccountState.PASSWORD_ACCOUNT);
        assertFalse(decide(false, autoLogin, null));
        assertNull(notices.getIfPresent(address));
        verifyNoInteractions(integrator);
        verify(hook, never()).forceLogin(any());
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
