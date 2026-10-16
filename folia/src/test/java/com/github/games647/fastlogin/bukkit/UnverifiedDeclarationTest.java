/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.listener.BungeeListener;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginMethodDecider;
import com.github.games647.fastlogin.core.shared.ForwardingAttributes;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.google.common.io.ByteStreams;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class UnverifiedDeclarationTest {
    private final UUID proxy = UUID.randomUUID();
    private final UUID uuid = UUID.nameUUIDFromBytes("OfflinePlayer:TestUser".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
    private FastLoginBukkit plugin;
    private FastLoginCore core;
    private AuthPlugin hook;
    private BungeeManager manager;
    private Player player;
    private com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        core = mock(FastLoginCore.class);
        hook = mock(AuthPlugin.class);
        manager = mock(BungeeManager.class);
        integrator = mock(com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator.class);
        set("authMePremiumIntegrator", integrator);
        com.github.games647.fastlogin.core.shared.PendingRelayStore relays =
                mock(com.github.games647.fastlogin.core.shared.PendingRelayStore.class);
        when(relays.getToggle(anyString())).thenReturn(null);
        set("pendingRelayStore", relays);
        set("passwordNotices", com.google.common.cache.CacheBuilder.newBuilder().build());
        set("core", core);
        set("logger", mock(Logger.class));
        set("bungeeManager", manager);
        set("preLoginAttestations", new ConcurrentHashMap<String, UUID>());
        set("crackedOverrides", new ConcurrentHashMap<String, Long>());
        doReturn(plugin).when(core).getPlugin();
        doReturn("FastLoginPlus").when(plugin).getName();
        doReturn(mock(FileConfiguration.class)).when(plugin).getConfig();
        doReturn(new ConcurrentHashMap<>()).when(plugin).getPremiumPlayers();
        when(core.getAuthPluginHook()).thenReturn(hook);
        when(core.getMessage("ownership-conflict")).thenReturn("ownership-conflict");
        when(manager.isEnabled()).thenReturn(true);
        when(manager.isSignedForwarding()).thenReturn(true);
        when(manager.isProxyAllowed(proxy)).thenReturn(true);
        when(hook.inspectAccount("TestUser")).thenReturn(LoginMethodDecider.AccountState.PREMIUM_OWNED);
        player = mock(Player.class);
        when(player.getName()).thenReturn("TestUser");
        when(player.getUniqueId()).thenReturn(uuid);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(spigot.getRawAddress()).thenReturn(address);
        doNothing().when(plugin).putSession(any(), any());
    }

    private IdentityClaim.Facts facts() {
        return new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, false, true, false);
    }

    private byte[] message(UUID target) {
        LoginActionMessage message = new LoginActionMessage(LoginActionMessage.Type.CRACKED, "TestUser", proxy, null);
        message.setFacts(facts());
        message.setDeclaration(IdentityClaim.Decision.OFFLINE, target);
        com.google.common.io.ByteArrayDataOutput output = ByteStreams.newDataOutput();
        message.writeTo(output);
        return output.toByteArray();
    }

    @Test
    void lateUnverifiedDeclarationBuildsLockedSessionAndSchedulesRejection() {
        when(manager.didJoinEventFired(player)).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> {
            Consumer<ScheduledTask> action = invocation.getArgument(1);
            action.accept(mock(ScheduledTask.class));
            return null;
        }).when(scheduler).run(eq(plugin), any(), isNull());
        io.papermc.paper.threadedregions.scheduler.AsyncScheduler async =
                mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
        doAnswer(invocation -> {
            Consumer<ScheduledTask> action = invocation.getArgument(1);
            action.accept(mock(ScheduledTask.class));
            return null;
        }).when(async).runNow(eq(plugin), any());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(async);
            new BungeeListener(plugin).onPluginMessageReceived("fastloginplus:force", player, message(uuid));
        }
        ArgumentCaptor<BukkitLoginSession> captured = ArgumentCaptor.forClass(BukkitLoginSession.class);
        verify(plugin).putSession(eq(address), captured.capture());
        BukkitLoginSession session = captured.getValue();
        assertFalse(session.isVerifiedPremium());
        assertNull(session.getUuid());
        assertEquals(IdentityClaim.Decision.OFFLINE, session.getIdentityDecision());
        assertEquals(com.github.games647.fastlogin.core.PremiumStatus.CRACKED,
                plugin.getPremiumPlayers().get(uuid));
        verify(player).kickPlayer("ownership-conflict");
        verify(hook, never()).forceLogin(any());
        verify(hook, never()).forceRegister(any(), anyString());
        verify(plugin, never()).getAuthMePremiumIntegrator();
    }

    @Test
    void wrongProxyOrWrongConnectionCannotApplyADeclaration() {
        when(manager.isProxyAllowed(proxy)).thenReturn(false);
        new BungeeListener(plugin).onPluginMessageReceived("fastloginplus:force", player, message(uuid));
        when(manager.isProxyAllowed(proxy)).thenReturn(true);
        new BungeeListener(plugin).onPluginMessageReceived("fastloginplus:force", player, message(UUID.randomUUID()));
        verify(plugin, never()).putSession(any(), any());
        verify(integrator, never()).applyProxyAutoLogin(anyBoolean());
        assertTrue(plugin.getPremiumPlayers().isEmpty());
    }

    @Test
    void configureOfflineClaimRejectsBeforeLegacyPremiumShortcuts() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.OFFLINE, "TestUser", uuid, null, facts(), proxy);
        ConfigureConnection connection = new ConfigureConnection(new Profile(uuid, claim.encode()));
        Method configure = FastLoginBukkit.class.getDeclaredMethod("onPlayerConfigure", Object.class);
        configure.setAccessible(true);
        configure.invoke(plugin, new ConfigureEvent(connection));
        assertEquals(Component.text("ownership-conflict"), connection.reason);
        verify(core, never()).getResolver();
        verify(plugin, never()).getAuthMePremiumIntegrator();
        verify(integrator).applyProxyAutoLogin(false);
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
    }

    @Test
    void configureVerifiedPolicyOffSynchronizesBeforeAccountDecisionWithoutPremiumMarking() throws Exception {
        UUID mojang = UUID.randomUUID();
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "TestUser", mojang, mojang, facts(), proxy);
        ConfigureConnection connection = new ConfigureConnection(new Profile(mojang, claim.encode()));
        Method configure = FastLoginBukkit.class.getDeclaredMethod("onPlayerConfigure", Object.class);
        configure.setAccessible(true);
        configure.invoke(plugin, new ConfigureEvent(connection));
        org.mockito.InOrder order = inOrder(integrator, hook);
        order.verify(integrator).applyProxyAutoLogin(false);
        order.verify(hook).inspectAccount("TestUser");
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(integrator, never()).injectVerifiedUuid(anyString(), any());
        verify(plugin, never()).putSession(any(), any());
        verify(hook, never()).forceLogin(any());
    }

    @Test
    void lateFactSynchronizesEvenWhenTheCarrierWasAlreadyAuthenticated() {
        when(player.hasMetadata("FastLoginPlus")).thenReturn(true);
        new BungeeListener(plugin).onPluginMessageReceived("fastloginplus:force", player, message(uuid));
        verify(integrator).applyProxyAutoLogin(false);
        verify(plugin, never()).putSession(any(), any());
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
    }

    @Test
    void enabledPolicyUsesTrueAndMissingOrLegacyFactsDoNotInventAPolicy() {
        plugin.synchronizeAuthMePremiumPolicy(null);
        plugin.synchronizeAuthMePremiumPolicy(IdentityClaim.Facts.defaults());
        verifyNoInteractions(integrator);
        plugin.synchronizeAuthMePremiumPolicy(new IdentityClaim.Facts(
                false, false, IdentityClaim.Consent.NO, true, true, false));
        verify(integrator).applyProxyAutoLogin(true);
    }

    @Test
    void authMeReloadReassertsTheRememberedPolicyAfterTheCommandNotInsideIt() {
        when(integrator.hasProxyAutoLoginPolicy()).thenReturn(true);
        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler scheduler =
                mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        org.bukkit.event.server.ServerCommandEvent event = mock(org.bukkit.event.server.ServerCommandEvent.class);
        when(event.getCommand()).thenReturn("authme reload");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(scheduler);
            new com.github.games647.fastlogin.bukkit.listener.AuthMeTakeoverListener(plugin).onConsoleCommand(event);
        }
        ArgumentCaptor<Consumer<ScheduledTask>> action = ArgumentCaptor.forClass(Consumer.class);
        verify(scheduler).runDelayed(eq(plugin), action.capture(), eq(1L));
        verify(integrator, never()).reassertProxyAutoLogin();
        action.getValue().accept(mock(ScheduledTask.class));
        verify(integrator).reassertProxyAutoLogin();
    }

    @Test
    void unknownProxyPolicyDoesNotScheduleReloadWork() {
        io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler scheduler =
                mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(scheduler);
            plugin.reassertAuthMeTakeover("/authme reload while policy is unknown");
        }
        verifyNoInteractions(scheduler);
        verify(integrator, never()).reassertProxyAutoLogin();
        verify(integrator, never()).applyProxyAutoLogin(anyBoolean());
        verify(integrator, never()).unregisterPremiumPacketListener();
    }

    @Test
    void unsignedEarlyDeclarationRequiresTheSameProxyTokenAllowlist() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        when(manager.isProxyAllowed(proxy)).thenReturn(false);
        Profile profile = new Profile(uuid, new IdentityClaim(IdentityClaim.Decision.OFFLINE,
                "TestUser", uuid, null, facts(), proxy).encode());
        Method read = FastLoginBukkit.class.getDeclaredMethod("readIdentityDeclaration", Object.class,
                String.class, UUID.class);
        read.setAccessible(true);
        assertNull(read.invoke(plugin, profile, "TestUser", uuid));
        when(manager.isProxyAllowed(proxy)).thenReturn(true);
        assertEquals(IdentityClaim.Decision.OFFLINE,
                ((IdentityClaim) read.invoke(plugin, profile, "TestUser", uuid)).getDecision());
    }

    private void set(String name, Object value) throws Exception {
        Field field = FastLoginBukkit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    @Test
    void unsignedPropertyWithoutDeclarationCannotPrecreate() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        UUID premium = UUID.randomUUID();
        Profile profile = new Profile(uuid, null, premium);
        configureWithLookup(profile, premium);
        assertNoPremiumGrant();
        verify(core).getResolver();
        verify(plugin.getLog()).warn(contains("unsigned forwarding has no validated flp_identity"), eq("TestUser"));
    }

    @Test
    void unsignedV4WithoutDeclarationCannotRegainTrustFromMatchingNameLookup() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        UUID premium = UUID.randomUUID();
        configureWithLookup(new Profile(premium, null, null), premium);
        assertNoPremiumGrant();
        verify(core).getResolver();
    }

    @Test
    void malformedOrDuplicateDeclarationsDoNotAuthorizeUnsignedAttestation() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        UUID premium = UUID.randomUUID();
        configureWithLookup(new Profile(premium, "malformed", premium), premium);
        Profile duplicate = new Profile(premium, verifiedClaim(premium, proxy), premium);
        duplicate.duplicate = true;
        configureWithLookup(duplicate, premium);
        assertNoPremiumGrant();
    }

    @Test
    void disallowedAndWrongConnectionDeclarationsDoNotAuthorizeUnsignedAttestation() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        UUID premium = UUID.randomUUID();
        configureWithLookup(new Profile(premium, verifiedClaim(premium, null), premium), premium);
        configureWithLookup(new Profile(premium, verifiedClaim(premium, UUID.randomUUID()), premium), premium);
        configureWithLookup(new Profile(premium, verifiedClaim(UUID.randomUUID(), proxy), premium), premium);
        configureWithLookup(new Profile(premium, new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "OtherUser", premium, premium, facts(), proxy).encode(), premium), premium);
        assertNoPremiumGrant();
    }

    @Test
    void ordinaryPasswordAccountKeepsPasswordFlowWithAnUnverifiedTrustedClaim() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        when(hook.inspectAccount("TestUser")).thenReturn(LoginMethodDecider.AccountState.PASSWORD_ACCOUNT);
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.OFFLINE, "TestUser", uuid, null,
                new IdentityClaim.Facts(false, true, IdentityClaim.Consent.NO, true, true, false), proxy);
        configureWithLookup(new Profile(uuid, claim.encode(), UUID.randomUUID()), UUID.randomUUID());
        assertNoPremiumGrant();
        verify(core, never()).getResolver();
    }

    @Test
    void noForgeryStillCannotPrecreateOnUnsignedForwarding() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        configureWithLookup(new Profile(uuid, null, null), UUID.randomUUID());
        assertNoPremiumGrant();
    }

    @Test
    void trustedLegacyDeclarationKeepsBothAttestationShortcuts() throws Exception {
        when(manager.isSignedForwarding()).thenReturn(false);
        UUID premium = UUID.randomUUID();
        when(integrator.isAuthMePremiumEnabled()).thenReturn(true);
        String rewrittenClaim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "TestUser", uuid, premium,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, true, true, false), proxy).encode();
        configureWithLookup(new Profile(uuid, rewrittenClaim, premium), premium);
        configureWithLookup(new Profile(premium, verifiedClaim(premium, proxy), null), premium);
        verify(integrator, times(2)).markPlayerAsPremium("TestUser", premium);
        verify(core, never()).getResolver();
    }

    @Test
    void signedForwardingStillAcceptsBothShortcutsWithoutADeclaration() throws Exception {
        UUID premium = UUID.randomUUID();
        when(plugin.getConfig().getBoolean("autoLogin")).thenReturn(true);
        when(integrator.isAuthMePremiumEnabled()).thenReturn(true);
        configureWithLookup(new Profile(uuid, null, premium), premium);
        configureWithLookup(new Profile(premium, null, null), premium);
        verify(integrator, times(2)).markPlayerAsPremium("TestUser", premium);
        verify(core, never()).getResolver();
    }

    @Test
    void emptyLegacyAllowlistWarningExplainsNetworkIsolationWithoutWarningOnSignedForwarding() {
        Logger log = mock(Logger.class);
        BungeeManager.warnIfUntrustedLegacy(true, log);
        verifyNoInteractions(log);
        BungeeManager.warnIfUntrustedLegacy(false, log);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(log).warn(message.capture());
        assertTrue(message.getValue().contains("backend port must be reachable only by your proxy"));
        assertTrue(message.getValue().contains("direct connections can impersonate players"));
        assertTrue(message.getValue().contains("plaintext"));
    }

    private String verifiedClaim(UUID connection, UUID source) {
        return new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, "TestUser", connection, connection,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, true, true, false), source).encode();
    }

    private void configureWithLookup(Profile profile, UUID resolved) throws Exception {
        com.github.games647.craftapi.resolver.MojangResolver resolver =
                mock(com.github.games647.craftapi.resolver.MojangResolver.class);
        when(core.getResolver()).thenReturn(resolver);
        com.github.games647.craftapi.model.Profile result = mock(com.github.games647.craftapi.model.Profile.class);
        when(result.getId()).thenReturn(resolved);
        when(resolver.findProfile("TestUser")).thenReturn(java.util.Optional.of(result));
        plugin.recordPreLoginAttestation("TestUser", profile);
        FoliaScheduler scheduler = mock(FoliaScheduler.class);
        set("scheduler", scheduler);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(scheduler).runAsync(any(Runnable.class));
        Method configure = FastLoginBukkit.class.getDeclaredMethod("onPlayerConfigure", Object.class);
        configure.setAccessible(true);
        configure.invoke(plugin, new ConfigureEvent(new ConfigureConnection(profile)));
    }

    private void assertNoPremiumGrant() {
        verify(integrator, never()).injectVerifiedUuid(anyString(), any());
        verify(integrator, never()).markPlayerAsPremium(anyString(), any());
        verify(plugin, never()).putSession(any(), any());
        verify(hook, never()).forceLogin(any());
        verify(hook, never()).forceRegister(any(), anyString());
    }

    public static final class ConfigureEvent {
        private final ConfigureConnection connection;
        public ConfigureEvent(ConfigureConnection connection) { this.connection = connection; }
        public ConfigureConnection getConnection() { return connection; }
    }

    public static final class ConfigureConnection implements PlayerConnection {
        private final Profile profile;
        private Component reason;
        public ConfigureConnection(Profile profile) { this.profile = profile; }
        public Profile getProfile() { return profile; }
        public InetSocketAddress getClientAddress() { return new InetSocketAddress("127.0.0.1", 12345); }
        @Override
        public void disconnect(Component reason) { this.reason = reason; }
    }

    public static final class Profile {
        private final UUID uuid;
        private final String claim;
        private final UUID premium;
        private boolean duplicate;
        public Profile(UUID uuid, String claim) { this(uuid, claim, UUID.randomUUID()); }
        public Profile(UUID uuid, String claim, UUID premium) {
            this.uuid = uuid; this.claim = claim; this.premium = premium;
        }
        public String getName() { return "TestUser"; }
        public UUID getId() { return uuid; }
        public Collection<Property> getProperties() {
            java.util.List<Property> properties = new java.util.ArrayList<>();
            if (claim != null) {
                properties.add(new Property(IdentityClaim.PROPERTY_NAME, claim));
                if (duplicate) { properties.add(new Property(IdentityClaim.PROPERTY_NAME, claim)); }
            }
            if (premium != null) {
                properties.add(new Property(ForwardingAttributes.PREMIUM_UUID_LEGACY, premium.toString()));
            }
            return properties;
        }
    }

    public static final class Property {
        private final String name;
        private final String value;
        public Property(String name, String value) { this.name = name; this.value = value; }
        public String getName() { return name; }
        public String getValue() { return value; }
    }
}
