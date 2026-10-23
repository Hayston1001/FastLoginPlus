/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Registered only on Paper/Folia, before the optional backend Floodgate listener. */
public final class ProxyBedrockPreLoginListener implements Listener {
    private final FastLoginBukkit plugin;

    public ProxyBedrockPreLoginListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        plugin.getProxyBedrockPreLogin().capture(event.getName(), event.getUniqueId(), event.getPlayerProfile());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getProxyBedrockPreLogin().isHandled(event.getPlayer());
    }
}
