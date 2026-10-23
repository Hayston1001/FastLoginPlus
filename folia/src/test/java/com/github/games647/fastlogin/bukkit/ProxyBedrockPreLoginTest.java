/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.hook.AuthMeHook;
import com.github.games647.fastlogin.bukkit.listener.BungeeListener;
import com.github.games647.fastlogin.bukkit.listener.ProxyBedrockPreLoginListener;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.protocol.BedrockClaim;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.config.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class ProxyBedrockPreLoginTest {
    private static final String NAME = "Bedrock";
    private final UUID uuid = UUID.randomUUID();
    private final UUID proxy = UUID.randomUUID();
    private final InetSocketAddress raw = new InetSocketAddress("127.0.0.1", 33445);
    private final FastLoginBukkit plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
    private final FastLoginCore core = mock(FastLoginCore.class);
    private final AuthMeHook auth = mock(AuthMeHook.class);
    private final BungeeManager manager = mock(BungeeManager.class);
    private final ProxyBedrockPreLogin bridge = new ProxyBedrockPreLogin(plugin);

    @BeforeEach
    void setup() throws Exception {
        set("core", core);
        set("logger", mock(Logger.class));
        set("bungeeManager", manager);
        set("proxyBedrockPreLogin", bridge);
        doReturn(true).when(plugin).isServerFullyStarted();
        doReturn(mock(FileConfiguration.class)).when(plugin).getConfig();
        doReturn(plugin).when(core).getPlugin();
        when(core.getAuthPluginHook()).thenReturn(auth);
        Configuration config = new Configuration();
        config.set("autoLoginFloodgate", false);
        config.set("autoRegisterFloodgate", false);
        config.set("allowFloodgateNameConflict", false);
        when(core.getConfig()).thenReturn(config);
        when(manager.isEnabled()).thenReturn(true);
        when(manager.getAllowedProxyIds()).thenReturn(Collections.singleton(proxy));
        when(manager.isProxyAllowed(proxy)).thenReturn(true);
        when(auth.isPreLoginForceLoginSupported()).thenReturn(true);
        when(auth.registerPlayer(eq(NAME), anyString())).thenReturn(true);
        when(auth.forceLoginFromProxy(NAME)).thenReturn(true);
    }

    private void set(String name, Object value) throws Exception {
        Field field = FastLoginBukkit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private Profile profile(String login, String register) {
        return new Profile(new BedrockClaim(NAME, uuid, false, login, register, "true",
                System.currentTimeMillis(), UUID.randomUUID()).encode(proxy));
    }

    private void configure(Profile profile) throws Exception {
        bridge.capture(NAME, uuid, profile);
        Method handler = FastLoginBukkit.class.getDeclaredMethod("onPlayerConfigure", Object.class);
        handler.setAccessible(true);
        handler.invoke(plugin, new Configure(profile, raw));
    }

    @Test
    void noFloodgateFirstAndSecondJoinsUseProxyPolicyBeforeAuthMeGate() throws Exception {
        when(auth.isRegistered(NAME)).thenReturn(false, true);
        configure(profile("true", "true"));
        configure(profile("true", "true"));
        org.mockito.InOrder order = inOrder(auth);
        order.verify(auth).registerPlayer(eq(NAME), argThat(password -> password.length() == 32));
        order.verify(auth).forceLoginFromProxy(NAME);
        order.verify(auth).forceLoginFromProxy(NAME);
        verify(auth).registerPlayer(eq(NAME), anyString());
        verify(plugin, never()).getFloodgateService();
    }

    @Test
    void javaOldProxyAndForgedClaimsNeverCallAuthMe() throws Exception {
        Profile java = new Profile();
        bridge.capture(NAME, uuid, java);
        assertFalse(bridge.configure(java, NAME, uuid, raw));
        String wire = profile("true", "true").properties.get(0).value;
        configure(new Profile(wire.replace(NAME, "Other")));
        configure(new Profile(wire.substring(0, wire.lastIndexOf(':'))));
        configure(new Profile(wire.replace(uuid.toString(), UUID.randomUUID().toString())));
        configure(new Profile(wire, wire));
        when(manager.getAllowedProxyIds()).thenReturn(Collections.singleton(UUID.randomUUID()));
        configure(new Profile(wire));
        verifyNoInteractions(auth);
    }

    @Test
    void proxyDisabledPolicyAndReplayCannotAuthenticate() throws Exception {
        configure(profile("false", "true"));
        configure(profile("true", "false"));
        verify(auth, never()).forceLoginFromProxy(anyString());
        Profile valid = profile("true", "true");
        configure(valid);
        configure(valid);
        verify(auth).forceLoginFromProxy(NAME);
        verify(auth).registerPlayer(eq(NAME), anyString());
    }

    @Test
    void lateActionsCannotUndoBackendRequestAndMarkerNeverFollowsReconnect() throws Exception {
        configure(profile("true", "true"));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(NAME);
        when(player.getUniqueId()).thenReturn(uuid);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(spigot.getRawAddress()).thenReturn(raw);
        BungeeListener listener = new BungeeListener(plugin);
        for (LoginActionMessage.Type type : new LoginActionMessage.Type[]{
                LoginActionMessage.Type.REGISTER, LoginActionMessage.Type.LOGIN}) {
            ByteArrayDataOutput output = ByteStreams.newDataOutput();
            new LoginActionMessage(type, NAME, proxy, null).writeTo(output);
            listener.onPluginMessageReceived("fastloginplus:force", player, output.toByteArray());
        }
        verify(auth, never()).forceRegister(any(), anyString());
        verify(auth, never()).forceLogin(any());
        verify(plugin, never()).getFloodgateService();
        Player reconnect = mock(Player.class);
        when(reconnect.getName()).thenReturn(NAME);
        when(reconnect.getUniqueId()).thenReturn(uuid);
        assertFalse(bridge.isHandled(reconnect));
    }

    @Test
    void noFloodgateStartupDoesNotRegisterOptionalApiListener() {
        PluginManager plugins = mock(PluginManager.class);
        doReturn(false).when(plugin).hasBackendFloodgate();
        plugin.registerBedrockListeners(plugins);
        verify(plugins).registerEvents(isA(ProxyBedrockPreLoginListener.class), eq(plugin));
        verifyNoMoreInteractions(plugins);
    }

    private Player boundPlayer() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(NAME);
        when(player.getUniqueId()).thenReturn(uuid);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(spigot.getRawAddress()).thenReturn(raw);
        return player;
    }

    @Test
    void unsupportedAuthMeOwnsDecisionButNeverRequestsAuthenticationOrCreatesAnAccount() throws Exception {
        when(auth.isPreLoginForceLoginSupported()).thenReturn(false);
        configure(profile("true", "true"));
        Player player = boundPlayer();
        assertTrue(bridge.isHandled(player));
        assertEquals(com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth.Outcome.UNSUPPORTED,
                bridge.getOutcome(player));
        assertFalse(bridge.isAuthenticationRequested(player));
        verify(auth, never()).isRegistered(anyString());
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
        rejectLateActions(player);
    }

    @Test
    void malformedAndPolicyDeniedDeclarationsOwnDecisionWithoutAuthentication() throws Exception {
        configure(new Profile("invalid"));
        Player malformed = boundPlayer();
        assertTrue(bridge.isHandled(malformed));
        assertFalse(bridge.isAuthenticationRequested(malformed));
        rejectLateActions(malformed);
        configure(profile("false", "true"));
        Player denied = boundPlayer();
        assertTrue(bridge.isHandled(denied));
        assertFalse(bridge.isAuthenticationRequested(denied));
        assertEquals(com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth.Outcome.NO_AUTO_LOGIN,
                bridge.getOutcome(denied));
        rejectLateActions(denied);
        new com.github.games647.fastlogin.bukkit.task.ForceLoginTask(core, denied,
                new BukkitLoginSession(NAME, true)).run();
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void acceptedRequestIsNotConfusedWithOwnershipOrCompletedAuthentication() throws Exception {
        configure(profile("true", "true"));
        Player player = boundPlayer();
        assertTrue(bridge.isHandled(player));
        assertTrue(bridge.isAuthenticationRequested(player));
        assertEquals(com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth.Outcome
                .REGISTERED_AND_LOGIN_REQUESTED, bridge.getOutcome(player));
        verify(auth, never()).isAuthenticated(any());
    }

    private void rejectLateActions(Player player) {
        for (LoginActionMessage.Type type : new LoginActionMessage.Type[]{
                LoginActionMessage.Type.REGISTER, LoginActionMessage.Type.LOGIN}) {
            ByteArrayDataOutput output = ByteStreams.newDataOutput();
            new LoginActionMessage(type, NAME, proxy, null).writeTo(output);
            new BungeeListener(plugin).onPluginMessageReceived("fastloginplus:force", player, output.toByteArray());
        }
        verify(auth, never()).forceRegister(any(), anyString());
        verify(auth, never()).forceLogin(any());
    }

    public final class Profile {
        private final List<Property> properties = new ArrayList<>();
        Profile(String... wires) {
            for (String wire : wires) { properties.add(new Property(wire)); }
        }
        public String getName() { return NAME; }
        public UUID getId() { return uuid; }
        public List<Property> getProperties() { return properties; }
    }

    public static final class Property {
        private final String value;
        Property(String value) { this.value = value; }
        public String getName() { return BedrockClaim.PROPERTY_NAME; }
        public String getValue() { return value; }
    }

    public static final class Configure {
        private final Connection connection;
        Configure(Object profile, SocketAddress address) { connection = new Connection(profile, address); }
        public Connection getConnection() { return connection; }
    }

    public static final class Connection {
        private final Object profile;
        private final SocketAddress address;
        Connection(Object profile, SocketAddress address) { this.profile = profile; this.address = address; }
        public Object getProfile() { return profile; }
        public SocketAddress getAddress() { return address; }
    }
}
