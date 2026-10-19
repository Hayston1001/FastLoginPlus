/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.entity.Player;
import ultraauth.api.UltraAuthAPI;
import ultraauth.managers.PlayerManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Folia-adapted UltraAuthHook.
 *
 * Uses Entity.getScheduler() + CompletableFuture instead of
 * Bukkit.getScheduler().callSyncMethod().
 */
public class UltraAuthHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    public UltraAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        // Folia: use Entity.getScheduler() + CompletableFuture
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            if (UltraAuthAPI.isAuthenticated(player)) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
                }
                future.complete(false);
                return;
            }

            UltraAuthAPI.authenticatedPlayer(player);
            future.complete(UltraAuthAPI.isAuthenticated(player));
        }, null);

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLog().error("Failed to forceLogin player: {}", player, ex);
            return false;
        } catch (ExecutionException | TimeoutException ex) {
            plugin.getLog().error("Failed to forceLogin player: {}", player, ex);
            return false;
        }
    }

    @Override
    public boolean isRegistered(String playerName) {
        return UltraAuthAPI.isRegisterd(playerName);
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        UltraAuthAPI.setPlayerPasswordOnline(player, password);
        return PlayerManager.getInstance().checkPlayerPassword(player, password) && forceLogin(player);
    }
}
