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
 * Folia-adapted ConnectionListener.
 *
 * Key difference from Bukkit version:
 * - Uses Entity.getScheduler().runDelayed() instead of Bukkit.getScheduler().runTaskLater()
 * - Uses Bukkit.getAsyncScheduler().runNow() instead of Bukkit.getScheduler().runTaskAsynchronously()
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

        // Folia: use Entity.getScheduler() instead of Bukkit.getScheduler()
        player.getScheduler().runDelayed(plugin, task -> {
            delayForceLogin(player);
        }, null, DELAY_LOGIN);
    }

    private void delayForceLogin(Player player) {
        if (plugin.getProxyBedrockPreLogin() != null && plugin.getProxyBedrockPreLogin().isHandled(player)) {
            plugin.getBungeeManager().markJoinEventFired(player);
            return;
        }
        if (!player.isOnline() || !plugin.deliverPendingLogin(player)) {
            return;
        }
        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());

        if (session == null) {
            FloodgateService floodgateService = plugin.getFloodgateService();
            if (floodgateService != null) {
                FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(player.getUniqueId());
                if (floodgatePlayer != null) {
                    Runnable floodgateAuthTask = new FloodgateAuthTask(plugin.getCore(), player, floodgatePlayer);
                    // Folia: use AsyncScheduler instead of Bukkit.getScheduler()
                    Bukkit.getAsyncScheduler().runNow(plugin, task -> floodgateAuthTask.run());
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
            // Folia: use AsyncScheduler instead of Bukkit.getScheduler()
            Bukkit.getAsyncScheduler().runNow(plugin, task -> forceLoginTask.run());
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
