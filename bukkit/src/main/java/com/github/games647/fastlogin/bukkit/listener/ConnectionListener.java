/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.task.FloodgateAuthTask;
import com.github.games647.fastlogin.bukkit.task.ForceLoginTask;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.Metadatable;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/**
 * This listener tells authentication plugins weather the player has a premium account. So the
 * plugin can skip authentication.
 */
public class ConnectionListener implements Listener {

    private static final long DELAY_LOGIN = 20L / 2;

    private final FastLoginBukkit plugin;

    public ConnectionListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent loginEvent) {
        removeBlockedStatus(loginEvent.getPlayer());
        String denied = plugin.takePendingKick(loginEvent.getPlayer().getName());
        if (denied != null) {
            // a login refused by the login-method decision during the
            // configuration phase: kick before the player ever joins
            loginEvent.disallow(Result.KICK_OTHER, denied);
            return;
        }
        if (loginEvent.getResult() == Result.ALLOWED && !plugin.isServerFullyStarted()) {
            loginEvent.disallow(Result.KICK_OTHER, plugin.getCore().getMessage("not-started"));
        }
        if (loginEvent.getResult() == Result.ALLOWED) {
            plugin.observeSpigotIdentity("PlayerLoginEvent", loginEvent.getPlayer().getName(),
                    loginEvent.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoinWaitingHint(PlayerJoinEvent joinEvent) {
        if (!plugin.getBungeeManager().isEnabled()) {
            return;
        }

        Player player = joinEvent.getPlayer();
        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());
        if (session != null && !session.isFactsPresent()) {
            plugin.getCore().sendLocaleMessage("account-state-checking", player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent joinEvent) {
        Player player = joinEvent.getPlayer();
        plugin.observeSpigotIdentity("PlayerJoinEvent", player.getName(), player, false);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            delayForceLogin(player);
            // delay the login process to let auth plugins initialize the player
            // Magic number however as there is no direct event from those plugins
        }, DELAY_LOGIN);
    }

    private void delayForceLogin(Player player) {
        if (!player.isOnline() || !plugin.deliverPendingLogin(player)) {
            return;
        }
        // session exists so the player is ready for force login
        // cases: Paper (firing BungeeCord message before PlayerJoinEvent) or not running BungeeCord and already
        // having the login session from the login process
        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());

        if (session == null) {
            // Floodgate players usually don't have a session at this point
            // exception: if force login by bungee message had been delayed
            FloodgateService floodgateService = plugin.getFloodgateService();
            if (floodgateService != null) {
                FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(player.getUniqueId());
                if (floodgatePlayer != null) {
                    Runnable floodgateAuthTask = new FloodgateAuthTask(plugin.getCore(), player, floodgatePlayer);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, floodgateAuthTask);
                    plugin.getBungeeManager().markJoinEventFired(player);
                    return;
                }
            }

            String sessionId = plugin.getSessionId(player.spigot().getRawAddress());
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("No on-going login session for player: {} with ID {}. ", player, sessionId);
                plugin.getLog().info("Setups using Minecraft proxies will start delayed "
                    + "when the command from the proxy is received");
            }
        } else {
            Runnable forceLoginTask = new ForceLoginTask(plugin.getCore(), player, session);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, forceLoginTask);
        }

        plugin.getBungeeManager().markJoinEventFired(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent quitEvent) {
        Player player = quitEvent.getPlayer();

        removeBlockedStatus(player);
        player.removeMetadata("flp-password-notice", plugin);
        plugin.getCore().getPendingConfirms().remove(player.getUniqueId());
        plugin.getPremiumPlayers().remove(player.getUniqueId());
        plugin.getPlayerFloodgateState().remove(player.getUniqueId());
        plugin.getBungeeManager().cleanup(player);
    }

    private void removeBlockedStatus(Metadatable player) {
        player.removeMetadata(plugin.getName(), plugin);
    }
}
