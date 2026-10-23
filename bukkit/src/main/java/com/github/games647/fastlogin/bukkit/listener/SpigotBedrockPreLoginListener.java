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
import org.bukkit.event.player.PlayerLoginEvent;

/** Actual Spigot only. Admission and live-profile inspection happen in ConnectionListener. */
public final class SpigotBedrockPreLoginListener implements Listener {
    private final FastLoginBukkit plugin;

    public SpigotBedrockPreLoginListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED || !plugin.isServerFullyStarted()
                || plugin.isPaper() || !plugin.getBungeeManager().isEnabled()) {
            return;
        }
        if (plugin.getProxyBedrockPreLogin().loginSpigot(event.getPlayer())) {
            return;
        }
        // Compatibility for older proxies without the declaration. B was deferred from async
        // pre-login, and may only read local policy after A proved there is no declaration.
        if (plugin.hasBackendFloodgate()) {
            new FloodgatePreLoginListener(plugin).onSpigotLogin(event.getPlayer());
        }
    }
}
