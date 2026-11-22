/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

/** UserLogin 2.13.1's YAML and offline-UUID profile layout. */
public final class UserLoginHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;
    private final JavaPlugin host;
    private final Class<?> yamlStore;
    private final Method getDatabase;
    private final Method rawPassword;
    private final Method createPassword;
    private final Method getPlayerState;
    private final Method isLoggedIn;
    private final Method authenticate;
    private final Object login;
    private final Object register;

    public UserLoginHook(FastLoginBukkit plugin, JavaPlugin host) {
        this.plugin = plugin;
        this.host = Objects.requireNonNull(host, "UserLogin is not available");
        if (!host.getDescription().getVersion().equals("2.13.1")
                || !host.getClass().getName().equals("com.elchologamer.userlogin.UserLogin")) {
            throw new IllegalStateException("The UserLogin adapter requires version 2.13.1");
        }
        ClassLoader loader = host.getClass().getClassLoader();
        try {
            yamlStore = loader.loadClass("com.elchologamer.userlogin.database.YamlDB");
            Class<?> playerState = loader.loadClass("com.elchologamer.userlogin.ULPlayer");
            Class<?> authType = loader.loadClass("com.elchologamer.userlogin.api.types.AuthType");
            getDatabase = host.getClass().getMethod("getDB");
            rawPassword = yamlStore.getMethod("getRawPassword", UUID.class);
            createPassword = yamlStore.getMethod("createPassword", UUID.class, String.class);
            getPlayerState = playerState.getMethod("get", Player.class);
            isLoggedIn = playerState.getMethod("isLoggedIn");
            authenticate = playerState.getMethod("onAuthenticate", authType);
            login = authType.getField("LOGIN").get(null);
            register = authType.getField("REGISTER").get(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("UserLogin API does not match 2.13.1", error);
        }
        requireCompatibleConfig();
    }

    @Override
    public boolean isRegistered(String playerName) throws Exception {
        requireJavaName(playerName);
        return storedPassword(UUIDAdapter.generateOfflineId(playerName)) != null;
    }

    @Override
    public boolean forceLogin(Player player) {
        return AuthPlayerThread.call(plugin, player, () -> apply(player, null));
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return AuthPlayerThread.call(plugin, player, () -> apply(player, password));
    }

    private boolean apply(Player player, String newPassword) {
        try {
            String name = player.getName();
            requireJavaName(name);
            UUID key = UUIDAdapter.generateOfflineId(name);
            if (!key.equals(player.getUniqueId())) {
                // An online UUID may key a different password account.
                return false;
            }
            Object store = yamlStore.cast(getDatabase.invoke(host));
            String disk = storedPassword(key);
            String current = (String) rawPassword.invoke(store, key);
            if (!Objects.equals(disk, current) || (current == null) != (newPassword != null)) {
                return false;
            }
            if (newPassword != null) {
                createPassword.invoke(store, key, newPassword);
                String written = (String) rawPassword.invoke(store, key);
                // UserLogin's YAML writer can swallow a save error.
                if (written == null || !written.equals(storedPassword(key))) {
                    return false;
                }
            }
            Object state = getPlayerState.invoke(null, player);
            if (newPassword != null || !(boolean) isLoggedIn.invoke(state)) {
                authenticate.invoke(state, newPassword == null ? login : register);
            }
            return (boolean) isLoggedIn.invoke(state);
        } catch (Exception failure) {
            plugin.getLog().warn("UserLogin action not confirmed for {} ({})",
                    player.getName(), failure.getClass().getSimpleName());
            return false;
        }
    }

    private String storedPassword(UUID key) throws Exception {
        requireCompatibleConfig();
        YamlConfiguration saved = new YamlConfiguration();
        saved.load(new File(host.getDataFolder(), "playerData.yml"));
        Object value = saved.get(key.toString());
        if (value instanceof ConfigurationSection) {
            value = ((ConfigurationSection) value).get("password");
        }
        if (value == null) {
            return null;
        }
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalStateException("Unusable UserLogin account record");
        }
        return (String) value;
    }

    private void requireCompatibleConfig() {
        FileConfiguration config = host.getConfig();
        if (!host.isEnabled() || !"yaml".equals(config.getString("database.type", "yaml"))
                || !"playerData.yml".equals(config.getString("database.yaml.file", "playerData.yml"))
                || config.getBoolean("bungeecord.autoLogin") || config.getBoolean("bungeeCord.autoLogin")
                || config.getBoolean("ipRecords.enabled") || config.getLong("ipCache", -1) > 0) {
            throw new IllegalStateException("UserLogin requires default YAML storage and no native auto-login");
        }
    }

    private static void requireJavaName(String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Not a valid Java account name");
        }
    }
}
