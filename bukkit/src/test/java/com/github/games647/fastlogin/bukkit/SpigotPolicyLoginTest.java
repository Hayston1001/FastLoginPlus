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
        when(manager.getAllowedProxyIds()).thenReturn(java.util.Collections.singleton(proxy));
        when(core.isDebug()).thenReturn(true);
        when(core.getMessage("proxy-claim-required")).thenReturn("proxy-claim-required");
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
        PlayerLoginEvent event = login();
        new ConnectionListener(plugin).onPlayerLogin(event);
        assertEquals(PlayerLoginEvent.Result.ALLOWED, event.getResult());
        org.mockito.InOrder order = inOrder(integrator);
        order.verify(integrator).applyProxyAutoLogin(true);
        order.verify(integrator).isAuthMePremiumEnabled();
        order.verify(integrator).markPlayerAsPremium(NAME, uuid);
    }

    @Test
    void disabledPolicyReleasesUsingTheLiveConnectionUuid() throws Exception {
        giveClaim(false, proxy, uuid);
        when(integrator.releaseUnusablePlaceholder(NAME, uuid)).thenReturn(
                AuthMePremiumIntegrator.ReleaseOutcome.RELEASED);
        new ConnectionListener(plugin).onPlayerLogin(login());
        verify(integrator, times(1)).releaseUnusablePlaceholder(NAME, uuid);
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(integrator, never()).injectVerifiedUuid(anyString(), any());
    }

    @Test
    void untrustedOrWrongConnectionClaimsCannotSetTheGlobalPolicy() throws Exception {
        giveClaim(false, UUID.randomUUID(), uuid);
        PlayerLoginEvent untrusted = login();
        new ConnectionListener(plugin).onPlayerLogin(untrusted);
        assertEquals(PlayerLoginEvent.Result.KICK_OTHER, untrusted.getResult());
        giveClaim(false, proxy, UUID.randomUUID());
        PlayerLoginEvent mismatched = login();
        new ConnectionListener(plugin).onPlayerLogin(mismatched);
        assertEquals(PlayerLoginEvent.Result.KICK_OTHER, mismatched.getResult());
        verify(integrator, never()).applyProxyAutoLogin(anyBoolean());
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(core, never()).resetVerificationState(anyString());
    }

    @Test
    void missingDeclarationDiscardsUnsignedV4AtTheLiveLoginHook() throws Exception {
        when(((LiveProfileCarrier) player).getProfile()).thenReturn(new BeanProfile(null));
        PlayerLoginEvent event = login();
        new ConnectionListener(plugin).onPlayerLogin(event);
        assertEquals(PlayerLoginEvent.Result.KICK_OTHER, event.getResult());
        assertEquals("proxy-claim-required", event.getKickMessage());
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(integrator, never()).applyProxyAutoLogin(anyBoolean());
        verify(logger).warn(contains("Refusing login"), eq(NAME), eq(uuid));
        verify(logger, never()).warn(anyString(), any(Object.class));
        verify(plugin, never()).applyPremiumAtPreLogin(anyString(), any());
    }

    @Test
    void unavailableLiveProfileAlsoRefusesUnsignedV4() throws Exception {
        when(((LiveProfileCarrier) player).getProfile()).thenThrow(new IllegalStateException("unavailable"));
        PlayerLoginEvent event = login();
        new ConnectionListener(plugin).onPlayerLogin(event);
        assertEquals(PlayerLoginEvent.Result.KICK_OTHER, event.getResult());
        verifyNoInteractions(integrator);
    }

    @Test
    void alreadyDeniedLoginSkipsClaimAdmission() {
        PlayerLoginEvent event = login();
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "other-plugin");
        new ConnectionListener(plugin).onPlayerLogin(event);
        assertEquals("other-plugin", event.getKickMessage());
        verify(plugin, never()).observeSpigotIdentity(anyString(), anyString(), any(), eq(true));
    }

    @Test
    void offlineLoginDoesNotRequireAClaim() {
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + NAME).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(((LiveProfileCarrier) player).getProfile()).thenReturn(new BeanProfile(null));
        PlayerLoginEvent event = login();
        new ConnectionListener(plugin).onPlayerLogin(event);
        assertEquals(PlayerLoginEvent.Result.ALLOWED, event.getResult());
        verifyNoInteractions(integrator);
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
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, enabled, true, false), null);
        when(((LiveProfileCarrier) player).getProfile()).thenReturn(
                new BeanProfile(claim.encode(), IdentityClaim.sign(claim.encode(), source)));
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
        private final String signature;
        public BeanProfile(String claim) { this(claim, null); }
        public BeanProfile(String claim, String signature) { this.claim = claim; this.signature = signature; }
        public Collection<BeanProperty> getProperties() {
            java.util.List<BeanProperty> properties = new java.util.ArrayList<>();
            if (claim != null) { properties.add(new BeanProperty(IdentityClaim.PROPERTY_NAME, claim)); }
            if (signature != null) {
                properties.add(new BeanProperty(IdentityClaim.SIGNATURE_PROPERTY_NAME, signature));
            }
            return properties;
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
