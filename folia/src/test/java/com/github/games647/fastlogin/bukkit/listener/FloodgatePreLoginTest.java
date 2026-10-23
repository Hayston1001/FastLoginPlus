/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.BungeeManager;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.hook.AuthMeHook;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.config.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.slf4j.Logger;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class FloodgatePreLoginTest {
    private static final String NAME = ".Bedrock";
    private boolean authenticated = true;
    private final UUID uuid = UUID.randomUUID();
    private final FastLoginBukkit plugin = mock(FastLoginBukkit.class);
    private final FastLoginCore core = mock(FastLoginCore.class);
    private final AuthMeHook hook = mock(AuthMeHook.class);
    private final FloodgateService floodgate = mock(FloodgateService.class);
    private final FloodgatePlayer bedrock = mock(FloodgatePlayer.class);
    private final AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
    private final Configuration config = new Configuration();
    private final FloodgatePreLoginListener listener =
            new FloodgatePreLoginListener(plugin, new FloodgatePreLoginAuth(core));

    @BeforeEach
    void setUp() {
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        doReturn(plugin).when(core).getPlugin();
        when(core.getConfig()).thenReturn(config);
        config.set("autoRegisterFloodgate", true);
        config.set("autoLoginFloodgate", true);
        config.set("allowFloodgateNameConflict", true);
        when(plugin.isServerFullyStarted()).thenReturn(true);
        when(plugin.getFloodgateService()).thenReturn(floodgate);
        when(plugin.hasBackendFloodgate()).thenReturn(true);
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(event.getName()).thenReturn(NAME);
        when(event.getUniqueId()).thenReturn(uuid);
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        when(floodgate.getBedrockPlayer(uuid)).thenReturn(bedrock);
        when(hook.isPreLoginForceLoginSupported()).thenReturn(true);
        when(hook.registerPlayer(eq(NAME), anyString())).thenReturn(true);
        when(hook.forceLoginFromProxy(NAME)).thenReturn(true);
    }

    @Test
    void registersBeforeQueuingLoginWithoutPremiumOrPasswordNotification() throws Exception {
        listener.onAsyncPlayerPreLogin(event);
        InOrder order = inOrder(hook);
        order.verify(hook).registerPlayer(eq(NAME), argThat(password -> password.length() == 32));
        order.verify(hook).forceLoginFromProxy(NAME);
        verify(floodgate).rememberPreLoginRequest(bedrock, NAME);
        verify(plugin, never()).getAuthMePremiumIntegrator();
        verify(core, never()).sendLocaleMessage(anyString(), any());
        assertEquals(EventPriority.NORMAL, FloodgatePreLoginListener.class
                .getMethod("onAsyncPlayerPreLogin", AsyncPlayerPreLoginEvent.class)
                .getAnnotation(EventHandler.class).priority());
    }

    @Test
    void existingAccountAndRepeatedConnectionDoNotRegisterAgain() {
        when(hook.isRegistered(NAME)).thenReturn(false, true);
        listener.onAsyncPlayerPreLogin(event);
        listener.onAsyncPlayerPreLogin(event);
        verify(hook).registerPlayer(eq(NAME), anyString());
        verify(hook, times(2)).forceLoginFromProxy(NAME);
    }

    @Test
    void javaConnectionDoesNotCallAuthMe() {
        when(floodgate.getBedrockPlayer(uuid)).thenReturn(null);
        listener.onAsyncPlayerPreLogin(event);
        verifyNoInteractions(hook);
    }

    @Test
    void missingBackendFloodgateLeavesProxyOnlyPathAlone() {
        when(plugin.getFloodgateService()).thenReturn(null);
        listener.onAsyncPlayerPreLogin(event);
        verifyNoInteractions(hook, floodgate);
    }

    @Test
    void proxyDeclarationTakesPrecedenceOverBackendPolicy() {
        com.github.games647.fastlogin.bukkit.ProxyBedrockPreLogin proxy =
                mock(com.github.games647.fastlogin.bukkit.ProxyBedrockPreLogin.class);
        when(plugin.getProxyBedrockPreLogin()).thenReturn(proxy);
        when(proxy.hasDeclaration(NAME, uuid)).thenReturn(true);
        listener.onAsyncPlayerPreLogin(event);
        verifyNoInteractions(hook, floodgate);
    }

    @Test
    void priorityOrdersCaptureBeforeFallbackEvenWhenFallbackIsRegisteredFirst() throws Exception {
        com.github.games647.fastlogin.bukkit.ProxyBedrockPreLogin proxy =
                mock(com.github.games647.fastlogin.bukkit.ProxyBedrockPreLogin.class);
        when(plugin.getProxyBedrockPreLogin()).thenReturn(proxy);
        java.util.concurrent.atomic.AtomicBoolean captured = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call -> {
            captured.set(true);
            return null;
        }).when(proxy).capture(eq(NAME), eq(uuid), any());
        when(proxy.hasDeclaration(NAME, uuid)).thenAnswer(call -> captured.get());
        java.lang.reflect.Method capture = ProxyBedrockPreLoginListener.class
                .getMethod("onPreLogin", AsyncPlayerPreLoginEvent.class);
        java.lang.reflect.Method fallback = FloodgatePreLoginListener.class
                .getMethod("onAsyncPlayerPreLogin", AsyncPlayerPreLoginEvent.class);
        EventPriority capturePriority = capture.getAnnotation(EventHandler.class).priority();
        EventPriority fallbackPriority = fallback.getAnnotation(EventHandler.class).priority();
        assertEquals(EventPriority.LOWEST, capturePriority);
        assertTrue(capturePriority.getSlot() < fallbackPriority.getSlot());
        assertTrue(fallbackPriority.getSlot() < EventPriority.HIGHEST.getSlot());
        // Supply B first, then dispatch using Bukkit's priority slots, never registration order.
        java.util.List<java.lang.reflect.Method> handlers = java.util.Arrays.asList(fallback, capture);
        handlers.sort(java.util.Comparator.comparingInt(
                method -> method.getAnnotation(EventHandler.class).priority().getSlot()));
        for (java.lang.reflect.Method handler : handlers) {
            handler.invoke(handler == capture ? new ProxyBedrockPreLoginListener(plugin) : listener, event);
        }
        verify(proxy).capture(NAME, uuid, null);
        verify(proxy).hasDeclaration(NAME, uuid);
        verifyNoInteractions(hook, floodgate);
    }

    @Test
    void missingOrDifferentAuthHookIsNotUsed() {
        AuthPlugin other = mock(AuthPlugin.class);
        when(core.getAuthPluginHook()).thenReturn(other).thenReturn(null);
        listener.onAsyncPlayerPreLogin(event);
        listener.onAsyncPlayerPreLogin(event);
        verifyNoInteractions(other, hook);
    }

    @Test
    void disabledRegistrationAndOlderApiDoNotCreateAccounts() {
        config.set("autoRegisterFloodgate", false);
        listener.onAsyncPlayerPreLogin(event);
        config.set("autoRegisterFloodgate", true);
        when(hook.isPreLoginForceLoginSupported()).thenReturn(false);
        listener.onAsyncPlayerPreLogin(event);
        verify(hook, never()).registerPlayer(anyString(), anyString());
        verify(hook, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void disabledLoginKeepsExistingAccountLocked() {
        config.set("autoLoginFloodgate", false);
        when(hook.isRegistered(NAME)).thenReturn(true);
        listener.onAsyncPlayerPreLogin(event);
        verify(hook, never()).registerPlayer(anyString(), anyString());
        verify(hook, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void disabledLoginAlsoPreventsNewRegistrationAndMarker() {
        config.set("autoLoginFloodgate", false);
        listener.onAsyncPlayerPreLogin(event);
        verify(hook, never()).registerPlayer(anyString(), anyString());
        verify(hook, never()).forceLoginFromProxy(anyString());
        verify(floodgate, never()).rememberPreLoginRequest(any(), anyString());
    }

    @Test
    void failedNewLoginRequestsCleanupWithoutRememberingSuccess() {
        when(hook.forceLoginFromProxy(NAME)).thenReturn(false);
        when(hook.unregisterPlayer(NAME)).thenReturn(true);
        listener.onAsyncPlayerPreLogin(event);
        verify(hook).registerPlayer(eq(NAME), anyString());
        verify(hook).unregisterPlayer(NAME);
        verify(floodgate, never()).rememberPreLoginRequest(any(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = LoginActionMessage.Type.class, names = {"REGISTER", "LOGIN"})
    void pendingBackendRequestSuppressesLateMessagesBeforeAuthMeCompletes(LoginActionMessage.Type type) {
        authenticated = false;
        when(floodgate.hasPreLoginRequest(bedrock, NAME)).thenReturn(true);
        repeatedLateProxyMessagesDoNotReplaceBackendLogin(type);
        verify(hook, never()).isAuthenticated(any());
    }

    @Test
    void failedRegistrationDoesNotApproveLogin() {
        when(hook.registerPlayer(eq(NAME), anyString())).thenReturn(false);
        listener.onAsyncPlayerPreLogin(event);
        verify(hook, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void deniedOrStartingConnectionDoesNotTouchAuthMe() {
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        listener.onAsyncPlayerPreLogin(event);
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        when(plugin.isServerFullyStarted()).thenReturn(false);
        listener.onAsyncPlayerPreLogin(event);
        verifyNoInteractions(hook);
    }

    @ParameterizedTest
    @EnumSource(value = LoginActionMessage.Type.class, names = {"REGISTER", "LOGIN"})
    void repeatedLateProxyMessagesDoNotReplaceBackendLogin(LoginActionMessage.Type type) {
        listener.onAsyncPlayerPreLogin(event);
        BungeeManager manager = mock(BungeeManager.class);
        when(plugin.getBungeeManager()).thenReturn(manager);
        UUID proxy = UUID.randomUUID();
        when(manager.isProxyAllowed(proxy)).thenReturn(true);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(NAME);
        when(player.getUniqueId()).thenReturn(uuid);
        when(hook.isAuthenticated(player)).thenReturn(authenticated);
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        new LoginActionMessage(type, NAME, proxy, null).writeTo(output);
        BungeeListener proxyListener = new BungeeListener(plugin);
        proxyListener.onPluginMessageReceived("fastloginplus:force", player, output.toByteArray());
        proxyListener.onPluginMessageReceived("fastloginplus:force", player, output.toByteArray());
        verify(hook).registerPlayer(eq(NAME), anyString());
        verify(hook, never()).forceRegister(any(), anyString());
        verify(hook, never()).forceLogin(any());
        verify(plugin, never()).putSession(any(), any());
        verify(plugin, never()).synchronizeAuthMePremiumPolicy(any());
    }
}
