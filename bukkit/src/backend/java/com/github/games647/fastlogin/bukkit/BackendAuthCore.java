/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.hook.AuthMeHook;
import com.github.games647.fastlogin.bukkit.hook.CrazyLoginHook;
import com.github.games647.fastlogin.bukkit.hook.LogItHook;
import com.github.games647.fastlogin.bukkit.hook.LoginSecurityHook;
import com.github.games647.fastlogin.bukkit.hook.MyLoginHook;
import com.github.games647.fastlogin.bukkit.hook.PasskyHook;
import com.github.games647.fastlogin.bukkit.hook.UltraAuthHook;
import com.github.games647.fastlogin.bukkit.hook.UserLoginHook;
import com.github.games647.fastlogin.bukkit.hook.XAuthHook;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** The backend's startup-only registration slot. Proxy cores keep their original API behaviour. */
final class BackendAuthCore extends FastLoginCore<Player, CommandSender, FastLoginBukkit> implements Listener {

    private enum Provider {
        AUTH_ME("AuthMe", plugin -> new AuthMeHook(plugin)),
        CRAZY_LOGIN("CrazyLogin", plugin -> new CrazyLoginHook(plugin)),
        LOG_IT("LogIt", plugin -> new LogItHook(plugin)),
        LOGIN_SECURITY("LoginSecurity", plugin -> new LoginSecurityHook(plugin)),
        ULTRA_AUTH("UltraAuth", plugin -> new UltraAuthHook(plugin)),
        X_AUTH("xAuth", plugin -> new XAuthHook(plugin)),
        PASSKY("Passky", plugin -> new PasskyHook(plugin)),
        USER_LOGIN("UserLogin", plugin -> new UserLoginHook(plugin,
                (JavaPlugin) plugin.getServer().getPluginManager().getPlugin("UserLogin"))),
        MY_LOGIN("MyLogin", plugin -> new MyLoginHook(plugin,
                (JavaPlugin) plugin.getServer().getPluginManager().getPlugin("MyLogin")));

        final String name;
        final Function<FastLoginBukkit, AuthPlugin<Player>> create;

        Provider(String name, Function<FastLoginBukkit, AuthPlugin<Player>> create) {
            this.name = name;
            this.create = create;
        }
    }

    private boolean sealed;
    private Plugin owner;

    BackendAuthCore(FastLoginBukkit plugin) {
        super(plugin);
    }

    @Override
    public synchronized void setAuthPluginHook(AuthPlugin<Player> hook) {
        AuthPlugin<Player> existing = getAuthPluginHook();
        if (existing == hook) {
            return;
        }
        if (!sealed && existing == null) {
            super.setAuthPluginHook(hook);
        } else {
            getPlugin().getLog().warn("Ignoring an auth hook change: the backend provider is already selected");
        }
    }

    synchronized boolean select(PluginManager manager, boolean proxy) {
        if (sealed) {
            throw new IllegalStateException("Backend authentication was already initialized");
        }
        sealed = true;
        FastLoginBukkit plugin = getPlugin();
        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        Provider winner = null;
        for (Provider provider : Provider.values()) {
            if (manager.isPluginEnabled(provider.name)) {
                enabled.add(provider.name);
                if (winner == null) {
                    winner = provider;
                }
            } else if (manager.getPlugin(provider.name) != null) {
                disabled.add(provider.name);
            }
        }
        AuthPlugin<Player> external = getAuthPluginHook();
        if (external != null) {
            try {
                owner = JavaPlugin.getProvidingPlugin(external.getClass());
            } catch (IllegalArgumentException unknownLoader) {
                // Delegating or embedded adapters need not use a Bukkit plugin class loader.
            }
            if (owner != null && owner != plugin) {
                manager.registerEvents(this, plugin);
            }
            plugin.getLog().info("Selected self-registered auth hook {}; skipped built-ins: {}",
                    external.getClass().getName(), enabled);
            // External plugins own their listeners, as before.
            return true;
        }
        if (winner == null) {
            if (proxy && disabled.isEmpty()) {
                plugin.getLog().info("No backend auth provider; continuing in proxy-only mode");
                return true;
            }
            plugin.getLog().error("No enabled backend auth provider; logins remain closed (disabled: {})", disabled);
            return false;
        }

        try {
            AuthPlugin<Player> hook = winner.create.apply(plugin);
            if (hook instanceof Listener) {
                manager.registerEvents((Listener) hook, plugin);
            }
            if (winner == Provider.AUTH_ME) {
                plugin.startAuthMe();
            }
            owner = manager.getPlugin(winner.name);
            manager.registerEvents(this, plugin);
            super.setAuthPluginHook(hook);
            plugin.getLog().info("Selected backend auth provider {} (priority order: {})", winner.name, enabled);
            return true;
        } catch (RuntimeException | LinkageError failed) {
            plugin.getLog().error("Cannot initialize {}; logins remain closed, no fallback to another account store",
                    winner.name, failed);
            return false;
        }
    }

    @EventHandler
    public void onProviderDisabled(PluginDisableEvent event) {
        if (event.getPlugin() == owner) {
            getPlugin().stopAuthentication();
            getPlugin().getLog().error("Selected auth provider {} stopped; restart to select a provider",
                    owner.getName());
        }
    }
}
