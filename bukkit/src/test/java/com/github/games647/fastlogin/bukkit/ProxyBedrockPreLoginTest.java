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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        doReturn(true).when(plugin).isPaper();
        doReturn(true).when(plugin).isServerFullyStarted();
        doReturn(mock(FileConfiguration.class)).when(plugin).getConfig();
        doReturn(plugin).when(core).getPlugin();
        when(core.getAuthPluginHook()).thenReturn(auth);
        // Deliberately contrary backend policy. A must only use the proxy's policy.
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
        when(auth.unregisterPlayer(NAME)).thenReturn(true);
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
    void noBackendFloodgateRegistersBeforeLoginAndSecondConnectionOnlyLogsIn() throws Exception {
        when(auth.isRegistered(NAME)).thenReturn(false, true);
        configure(profile("true", "true"));
        configure(profile("true", "true"));
        org.mockito.InOrder order = inOrder(auth);
        order.verify(auth).registerPlayer(eq(NAME), argThat(password -> password.length() == 32));
        order.verify(auth).forceLoginFromProxy(NAME);
        order.verify(auth).forceLoginFromProxy(NAME);
        verify(auth, times(1)).registerPlayer(eq(NAME), anyString());
        verify(plugin, never()).getFloodgateService();
        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void javaAndOldProxyWithoutFactMakeNoAuthMeCalls() throws Exception {
        Profile java = new Profile();
        bridge.capture(NAME, uuid, java);
        assertFalse(bridge.hasDeclaration(NAME, uuid));
        assertFalse(bridge.configure(java, NAME, uuid, raw));
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @CsvSource({"false,true,false", "true,false,false", "false,true,true", "linked,true,false"})
    void disabledProxyPolicyKeepsPasswordLock(String login, String register, boolean existing) throws Exception {
        when(auth.isRegistered(NAME)).thenReturn(existing);
        configure(profile(login, register));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void rejectsForgeriesWrongBindingsDuplicatesAndDisallowedProxies() throws Exception {
        Profile valid = profile("true", "true");
        String wire = valid.properties.get(0).value;
        List<Profile> rejected = new ArrayList<>();
        rejected.add(new Profile(wire.replace(":true:", ":false:")));
        rejected.add(new Profile(wire.substring(0, wire.lastIndexOf(':'))));
        rejected.add(new Profile(wire.replace(NAME, "Another")));
        rejected.add(new Profile(wire.replace(uuid.toString(), UUID.randomUUID().toString())));
        rejected.add(new Profile(new BedrockClaim(NAME, uuid, false, "true", "true", "true",
                System.currentTimeMillis(), UUID.randomUUID()).encode(UUID.randomUUID())));
        rejected.add(new Profile(wire, wire));
        for (Profile profile : rejected) {
            configure(profile);
        }
        verifyNoInteractions(auth);
    }

    @Test
    void cacheOnlyChangedProfilesAndNonceReplayCannotAutoAuth() throws Exception {
        Profile valid = profile("true", "true");
        assertTrue(bridge.configure(valid, NAME, uuid, raw));
        verifyNoInteractions(auth);
        bridge.capture(NAME, uuid, valid);
        assertTrue(bridge.configure(profile("true", "true"), NAME, uuid, raw));
        verifyNoInteractions(auth);
        configure(valid);
        configure(valid);
        verify(auth).registerPlayer(eq(NAME), anyString());
        verify(auth).forceLoginFromProxy(NAME);
    }

    @Test
    void failedNewLoginCleansOnlyTheNewAccount() throws Exception {
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        configure(profile("true", "true"));
        verify(auth).unregisterPlayer(NAME);
    }

    @Test
    void failedExistingLoginNeverDeletesAccount() throws Exception {
        when(auth.isRegistered(NAME)).thenReturn(true);
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        configure(profile("true", "true"));
        verify(auth, never()).unregisterPlayer(anyString());
    }

    @Test
    void lateRegisterAndLoginAreIgnoredWithoutBackendFloodgate() throws Exception {
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
        verify(auth).registerPlayer(eq(NAME), anyString());
        verify(auth, never()).forceRegister(any(), anyString());
        verify(auth, never()).forceLogin(any());
        verify(auth, never()).isAuthenticated(any());
        verify(plugin, never()).getFloodgateService();
        Player reconnect = mock(Player.class);
        when(reconnect.getName()).thenReturn(NAME);
        when(reconnect.getUniqueId()).thenReturn(uuid);
        assertFalse(bridge.isHandled(reconnect));
    }

    @Test
    void startupWithoutFloodgateRegistersOnlyTheApiFreeListener() {
        PluginManager plugins = mock(PluginManager.class);
        doReturn(false).when(plugin).hasBackendFloodgate();
        plugin.registerBedrockListeners(plugins);
        verify(plugins).registerEvents(isA(ProxyBedrockPreLoginListener.class), eq(plugin));
        verifyNoMoreInteractions(plugins);
    }

    @Test
    void aPathLoadsAndAuthenticatesWithFloodgateApiHidden() throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader isolated = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.geysermc.floodgate.")) {
                    throw new ClassNotFoundException("Floodgate intentionally absent");
                }
                if (name.startsWith(ProxyBedrockPreLogin.class.getName())
                        || name.startsWith(SpigotProfileReader.class.getName())
                        || name.startsWith("com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth")
                        || name.equals("com.github.games647.fastlogin.core.hooks.bedrock.BedrockSession")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try (java.io.InputStream stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            byte[] bytes = ByteStreams.toByteArray(stream);
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (java.io.IOException error) {
                            throw new ClassNotFoundException(name, error);
                        }
                    }
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        };
        Class<?> type = isolated.loadClass(ProxyBedrockPreLogin.class.getName());
        Object instance = type.getConstructor(FastLoginBukkit.class).newInstance(plugin);
        Profile profile = profile("true", "true");
        type.getMethod("capture", String.class, UUID.class, Object.class).invoke(instance, NAME, uuid, profile);
        assertEquals(true, type.getMethod("configure", Object.class, String.class, UUID.class, SocketAddress.class)
                .invoke(instance, profile, NAME, uuid, raw));
        verify(auth).registerPlayer(eq(NAME), anyString());
        verify(auth).forceLoginFromProxy(NAME);
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
