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
import java.util.Arrays;
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

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        core = mock(FastLoginCore.class);
        hook = mock(AuthPlugin.class);
        manager = mock(BungeeManager.class);
        set("core", core);
        set("logger", mock(Logger.class));
        set("bungeeManager", manager);
        set("preLoginAttestations", new ConcurrentHashMap<String, UUID>());
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
        assertTrue(plugin.getPremiumPlayers().isEmpty());
    }

    @Test
    void configureOfflineClaimRejectsBeforeLegacyPremiumShortcuts() throws Exception {
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.OFFLINE, "TestUser", uuid, null, facts(), proxy);
        ConfigureConnection connection = new ConfigureConnection(new Profile(uuid, claim.encode()));
        Method configure = FastLoginBukkit.class.getDeclaredMethod("onPlayerConfigure", Object.class);
        configure.setAccessible(true);
        configure.invoke(plugin, new ConfigureEvent(connection));
        assertEquals(Component.text("ownership-conflict"), connection.reason);
        verify(core, never()).getResolver();
        verify(plugin, never()).getAuthMePremiumIntegrator();
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
        public Profile(UUID uuid, String claim) { this.uuid = uuid; this.claim = claim; }
        public String getName() { return "TestUser"; }
        public UUID getId() { return uuid; }
        public Collection<Property> getProperties() {
            return Arrays.asList(new Property(IdentityClaim.PROPERTY_NAME, claim),
                    new Property(ForwardingAttributes.PREMIUM_UUID, UUID.randomUUID().toString()));
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
