/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Objects;

/** MyLogin 1.3.0's name-keyed file store and login lifecycle. */
public final class MyLoginHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;
    private final JavaPlugin host;
    private final Method getStorage;
    private final Method getRawData;
    private final Method register;
    private final Method checkPassword;
    private final Method isLoggedIn;
    private final Method setLoggedIn;
    private final Method updateIp;
    private final Method removeBlindness;
    private final Method playEffects;

    public MyLoginHook(FastLoginBukkit plugin, JavaPlugin host) {
        this.plugin = plugin;
        this.host = Objects.requireNonNull(host, "MyLogin is not available");
        if (!"1.3.0".equals(host.getDescription().getVersion())
                || !"com.acey.mylogin.MyLogin".equals(host.getClass().getName())) {
            throw new IllegalStateException("The MyLogin adapter requires version 1.3.0");
        }
        ClassLoader loader = host.getClass().getClassLoader();
        try {
            getStorage = host.getClass().getMethod("getUserStorage");
            Class<?> store = getStorage.getReturnType();
            getRawData = store.getMethod("getRawData");
            register = store.getMethod("registerPlayer", Player.class, String.class, String.class);
            checkPassword = store.getMethod("checkPassword", Player.class, String.class);
            isLoggedIn = store.getMethod("isLoggedIn", Player.class);
            setLoggedIn = store.getMethod("setLoggedIn", Player.class, boolean.class);
            updateIp = store.getMethod("updateIP", Player.class, String.class);
            playEffects = store.getMethod("playLoginEffects", Player.class);
            removeBlindness = loader.loadClass("com.acey.mylogin.utils.EffectUtils")
                    .getMethod("removeBlindness", Player.class);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("MyLogin API does not match 1.3.0", error);
        }
        requireCompatibleConfig();
    }

    @Override
    public boolean isRegistered(String playerName) throws Exception {
        return credential(readStore(), playerName) != null;
    }

    @Override
    public boolean forceLogin(Player player) {
        return AuthPlayerThread.call(plugin, player, () -> apply(player, null));
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return AuthPlayerThread.call(plugin, player, () -> apply(player, password));
    }

    private boolean apply(Player player, String password) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return false;
        }
        try {
            String name = player.getName();
            String ip = address.getAddress().getHostAddress();
            Object store = getStorage.invoke(host);
            ConfigurationSection memory = (ConfigurationSection) getRawData.invoke(store);
            String existing = credential(memory, name);
            if (!Objects.equals(existing, credential(readStore(), name)) || (existing == null) != (password != null)) {
                return false;
            }
            if (password != null) {
                if (!validPassword(password)) {
                    return false;
                }
                register.invoke(store, player, password, ip);
                String written = credential(memory, name);
                if (written == null || !written.equals(credential(readStore(), name))
                        || !(boolean) checkPassword.invoke(store, player, password)) {
                    return false;
                }
            }
            if (!(boolean) isLoggedIn.invoke(store, player)) {
                updateIp.invoke(store, player, ip);
                removeBlindness.invoke(null, player);
                playEffects.invoke(store, player);
                player.setWalkSpeed(0.2f);
                setLoggedIn.invoke(store, player, true);
            }
            return (boolean) isLoggedIn.invoke(store, player);
        } catch (Exception failure) {
            plugin.getLog().warn("MyLogin action not confirmed for {} ({})",
                    player.getName(), failure.getClass().getSimpleName());
            return false;
        }
    }

    private boolean validPassword(String password) {
        return password.length() >= host.getConfig().getInt("min-password-length", 4)
                && password.length() <= host.getConfig().getInt("max-password-length", 32)
                && password.matches("[A-Za-z0-9]+");
    }

    private ConfigurationSection readStore() throws Exception {
        requireCompatibleConfig();
        YamlConfiguration saved = new YamlConfiguration();
        saved.load(new File(host.getDataFolder(), "data/users.yml"));
        return saved;
    }

    private void requireCompatibleConfig() {
        if (!host.isEnabled() || host.getConfig().getBoolean("ip-remember", true)) {
            throw new IllegalStateException("MyLogin automatic authentication requires ip-remember: false");
        }
    }

    private static String credential(ConfigurationSection data, String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Not a valid Java account name");
        }
        for (String candidate : data.getKeys(false)) {
            if (candidate.equalsIgnoreCase(name) && !candidate.equals(name)) {
                throw new IllegalStateException("Conflicting MyLogin account spelling");
            }
        }
        if (!data.contains(name)) {
            return null;
        }
        ConfigurationSection account = data.getConfigurationSection(name);
        String hash = account == null ? null : account.getString("hash");
        if (hash == null || hash.isEmpty() || !account.getBoolean("registered")) {
            throw new IllegalStateException("Unusable MyLogin account record");
        }
        return hash + ':' + account.getString("salt", "");
    }
}
