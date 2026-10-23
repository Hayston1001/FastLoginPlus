/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/**
 * Applies the backend-assisted Floodgate auto-auth before AuthMe's login gate.
 *
 * <p>Runs at {@link EventPriority#LOWEST}: AuthMe's own pre-login checks run at
 * HIGHEST, and its pre-join dialog/login timeout starts during the configuration
 * phase. Creating the record and queueing the force login here means AuthMe can
 * skip the dialog and auto-login when the player actually joins.
 *
 * <p>The listener is inert unless Floodgate is installed on this backend and
 * identifies the connection as a Bedrock player. Normal proxy-only deployments
 * keep their existing proxy-side Floodgate path.
 */
public class FloodgatePreLoginListener implements Listener {

    private final FastLoginBukkit plugin;
    private final FloodgatePreLoginAuth preLoginAuth;

    public FloodgatePreLoginListener(FastLoginBukkit plugin) {
        this(plugin, new FloodgatePreLoginAuth(plugin.getCore()));
    }

    // Package-private for tests, which supply a mocked handler.
    FloodgatePreLoginListener(FastLoginBukkit plugin, FloodgatePreLoginAuth preLoginAuth) {
        this.plugin = plugin;
        this.preLoginAuth = preLoginAuth;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != Result.ALLOWED || !plugin.isServerFullyStarted()) {
            return;
        }

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (floodgateService == null) {
            return;
        }

        FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(event.getUniqueId());
        if (floodgatePlayer == null) {
            return;
        }

        AuthPlugin<Player> authPlugin = plugin.getCore().getAuthPluginHook();
        if (!(authPlugin instanceof PreLoginAuthPlugin)) {
            return;
        }

        FloodgatePreLoginAuth.Outcome outcome = preLoginAuth.handle(
                event.getName(), floodgatePlayer, (PreLoginAuthPlugin) authPlugin);
        if (outcome == FloodgatePreLoginAuth.Outcome.REGISTERED_AND_LOGIN_REQUESTED
                || outcome == FloodgatePreLoginAuth.Outcome.LOGIN_REQUESTED) {
            floodgateService.rememberPreLoginRequest(floodgatePlayer, event.getName());
        }
    }
}
