/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator;
import com.github.games647.fastlogin.bukkit.listener.ConnectionListener;
import com.github.games647.fastlogin.bukkit.listener.PreLoginPremiumListener;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginMethodDecider;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class SpigotPolicyLoginTest {
    private static final String NAME = "TestUser";
    private final UUID uuid = UUID.randomUUID();
    private final UUID proxy = UUID.randomUUID();
    private FastLoginBukkit plugin;
    private FastLoginCore core;
    private AuthMePremiumIntegrator integrator;
    private AuthPlugin hook;
    private BungeeManager manager;
    private Logger logger;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        core = mock(FastLoginCore.class);
        integrator = mock(AuthMePremiumIntegrator.class);
        hook = mock(AuthPlugin.class);
        manager = mock(BungeeManager.class);
        logger = mock(Logger.class);
        set("core", core);
        set("logger", logger);
        set("authMePremiumIntegrator", integrator);
        set("bungeeManager", manager);
        set("pendingKicks", new ConcurrentHashMap<String, String>());
        set("crackedOverrides", new ConcurrentHashMap<String, Long>());
        doReturn(true).when(plugin).isServerFullyStarted();
        doReturn("FastLoginPlus").when(plugin).getName();
        doReturn(mock(FileConfiguration.class)).when(plugin).getConfig();
        doReturn(integrator).when(plugin).getAuthMePremiumIntegrator();
        when(manager.isEnabled()).thenReturn(true);
        when(manager.isSignedForwarding()).thenReturn(false);
        when(manager.isProxyAllowed(proxy)).thenReturn(true);
        when(core.isDebug()).thenReturn(true);
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(hook.inspectAccount(NAME)).thenReturn(LoginMethodDecider.AccountState.PREMIUM_OWNED);
        player = mock(Player.class, withSettings().extraInterfaces(LiveProfileCarrier.class));
        when(player.getName()).thenReturn(NAME);
        when(player.getUniqueId()).thenReturn(uuid);
        // Even a stale true getter must not allow FLP marking when the declared decision is off.
        when(integrator.isAuthMePremiumEnabled()).thenReturn(true);
    }

    @Test
    void firstConnectionSynchronizesAtLoginBeforeJoinWithoutEarlierPremiumMarking() throws Exception {
        giveClaim(false, proxy, uuid);
        AsyncPlayerPreLoginEvent pre = mock(AsyncPlayerPreLoginEvent.class);
        when(pre.getName()).thenReturn(NAME);
        when(pre.getUniqueId()).thenReturn(uuid);
        when(pre.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        new PreLoginPremiumListener(plugin).onAsyncPlayerPreLogin(pre);
        verifyNoInteractions(integrator);
        ConnectionListener listener = new ConnectionListener(plugin);
        listener.onPlayerLogin(login());
        org.mockito.InOrder order = inOrder(integrator, hook);
        order.verify(integrator).applyProxyAutoLogin(false);
        order.verify(hook).inspectAccount(NAME);
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(integrator, never()).injectVerifiedUuid(anyString(), any());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            listener.onPlayerJoin(new PlayerJoinEvent(player, ""));
        }
        verify(integrator, times(1)).applyProxyAutoLogin(false);
        verify(logger).info("Spigot identity visibility: phase={} name={} source=no_player claimCount=unavailable",
                "AsyncPlayerPreLoginEvent", NAME);
        String message = "Spigot identity visibility: phase={} name={} source={} claimCount={} trusted={}";
        verify(logger).info(message, "PlayerLoginEvent", NAME, "getProfile", 1, true);
        verify(logger).info(message, "PlayerJoinEvent", NAME, "getProfile", 1, true);
    }

    @Test
    void enabledPolicyRetainsPremiumPrecreationBeforeJoin() throws Exception {
        giveClaim(true, proxy, uuid);
        new ConnectionListener(plugin).onPlayerLogin(login());
        org.mockito.InOrder order = inOrder(integrator);
        order.verify(integrator).applyProxyAutoLogin(true);
        order.verify(integrator).isAuthMePremiumEnabled();
        order.verify(integrator).markPlayerAsPremium(NAME, uuid);
    }

    @Test
    void untrustedOrWrongConnectionClaimsCannotSetTheGlobalPolicy() throws Exception {
        giveClaim(false, UUID.randomUUID(), uuid);
        new ConnectionListener(plugin).onPlayerLogin(login());
        giveClaim(false, proxy, UUID.randomUUID());
        new ConnectionListener(plugin).onPlayerLogin(login());
        verify(integrator, never()).applyProxyAutoLogin(anyBoolean());
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(core, never()).resetVerificationState(anyString());
    }

    @Test
    void missingDeclarationDiscardsUnsignedV4AtTheLiveLoginHook() throws Exception {
        when(((LiveProfileCarrier) player).getProfile()).thenReturn(new BeanProfile(null));
        new ConnectionListener(plugin).onPlayerLogin(login());
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(integrator, never()).applyProxyAutoLogin(anyBoolean());
        verify(logger).warn(contains("unsigned forwarding has no validated flp_identity"), eq(NAME));
    }

    @Test
    void legacyTwoArgumentEntryCannotAuthorizeV4WithoutADeclaration() {
        assertFalse(plugin.applyPremiumAtPreLogin(NAME, uuid));
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
    }

    @Test
    void signedLiveProfileKeepsItsMissingDeclarationFallback() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(true);
        when(plugin.getConfig().getBoolean("autoLogin")).thenReturn(true);
        when(((LiveProfileCarrier) player).getProfile()).thenReturn(new BeanProfile(null));
        new ConnectionListener(plugin).onPlayerLogin(login());
        verify(integrator).markPlayerAsPremium(NAME, uuid);
        verify(integrator, never()).applyProxyAutoLogin(anyBoolean());
    }

    @Test
    void standaloneDoesNotUseTheProxyPolicyLoginHook() throws Exception {
        when(manager.isEnabled()).thenReturn(false);
        giveClaim(false, proxy, uuid);
        new ConnectionListener(plugin).onPlayerLogin(login());
        verifyNoInteractions(integrator);
    }

    private void giveClaim(boolean enabled, UUID source, UUID forwarded) {
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                NAME, forwarded, forwarded,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, enabled, true, false), source);
        when(((LiveProfileCarrier) player).getProfile()).thenReturn(new BeanProfile(claim.encode()));
    }

    private PlayerLoginEvent login() {
        return new PlayerLoginEvent(player, "proxy.example", InetAddress.getLoopbackAddress());
    }

    private void set(String name, Object value) throws Exception {
        Field field = FastLoginBukkit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    public interface LiveProfileCarrier {
        Object getProfile();
    }

    public static final class BeanProfile {
        private final String claim;
        public BeanProfile(String claim) { this.claim = claim; }
        public Collection<BeanProperty> getProperties() {
            return claim == null ? java.util.Collections.emptyList()
                    : Arrays.asList(new BeanProperty(IdentityClaim.PROPERTY_NAME, claim));
        }
    }

    public static final class BeanProperty {
        private final String name;
        private final String value;
        public BeanProperty(String name, String value) { this.name = name; this.value = value; }
        public String getName() { return name; }
        public String getValue() { return value; }
    }
}
