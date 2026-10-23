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
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/**
 * Compatibility auto-auth for backends with Floodgate and no proxy Bedrock declaration.
 *
 * <p>Runs at {@link EventPriority#LOWEST}: AuthMe's own pre-login checks run at
 * HIGHEST, and its pre-join dialog/login timeout starts during the configuration
 * phase. Creating the record and queueing the force login here means AuthMe can
 * skip the dialog and auto-login when the player actually joins.
 *
 * <p>This fallback identifies the connection using backend Floodgate and reads
 * backend policy. A proxy declaration takes precedence, including when it is
 * rejected; local policy must not override the proxy-first path.
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
        if (plugin.getProxyBedrockPreLogin() != null
                && plugin.getProxyBedrockPreLogin().hasDeclaration(event.getName(), event.getUniqueId())) {
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
                event.getName(), BedrockSession.fromConfig(plugin.getCore().getConfig(),
                        floodgatePlayer.getLinkedPlayer() != null), (PreLoginAuthPlugin) authPlugin);
        if (outcome == FloodgatePreLoginAuth.Outcome.REGISTERED_AND_LOGIN_REQUESTED
                || outcome == FloodgatePreLoginAuth.Outcome.LOGIN_REQUESTED) {
            floodgateService.rememberPreLoginRequest(floodgatePlayer, event.getName());
        }
    }

    public static boolean isHandled(FastLoginBukkit plugin, Player player) {
        FloodgateService service = plugin.getFloodgateService();
        FloodgatePlayer bedrock = service == null ? null : service.getBedrockPlayer(player.getUniqueId());
        AuthPlugin<Player> hook = plugin.getCore().getAuthPluginHook();
        return bedrock != null && hook instanceof PreLoginAuthPlugin
                && (service.hasPreLoginRequest(bedrock, player.getName()) || hook.isAuthenticated(player));
    }
}
