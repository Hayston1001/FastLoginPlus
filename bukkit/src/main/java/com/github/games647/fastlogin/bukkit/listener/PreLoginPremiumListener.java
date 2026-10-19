/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Reports the profile visibility of Spigot's asynchronous pre-login phase.
 * Policy-aware pre-creation is deferred to the live PlayerLoginEvent profile,
 * before AuthMe's join-time premium handling.
 */
public class PreLoginPremiumListener implements Listener {

    private final FastLoginBukkit plugin;

    /**
     * Creates the listener.
     *
     * @param plugin the plugin instance observing phase visibility
     */
    public PreLoginPremiumListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs while the connection is still in the login phase, i.e. before AuthMe's join
     * handler decides whether to open a dialog.
     *
     * @param event the pre-login event of the joining connection
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        plugin.observeSpigotIdentity("AsyncPlayerPreLoginEvent", event.getName(), null, false);
        // Spigot's live forwarded profile is available at PlayerLoginEvent. Defer pre-creation
        // there as well, so it cannot precede a declared policy-off decision.
    }
}
