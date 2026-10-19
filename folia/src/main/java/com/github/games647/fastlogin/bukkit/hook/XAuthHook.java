/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import de.luricos.bukkit.xAuth.xAuth;
import de.luricos.bukkit.xAuth.xAuthPlayer;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Folia-adapted XAuthHook.
 *
 * Uses Entity.getScheduler() + CompletableFuture instead of
 * Bukkit.getScheduler().callSyncMethod().
 */
public class XAuthHook implements AuthPlugin<Player> {

    private final xAuth xAuthPlugin = xAuth.getPlugin();
    private final FastLoginBukkit plugin;

    public XAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        // Folia: use Entity.getScheduler() + CompletableFuture
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(player);
            if (xAuthPlayer != null) {
                if (xAuthPlayer.isAuthenticated()) {
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
                    }
                    future.complete(false);
                    return;
                }

                xAuthPlayer.setPremium(true);
                future.complete(xAuthPlugin.getPlayerManager().doLogin(xAuthPlayer));
                return;
            }

            future.complete(false);
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
        xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(playerName);
        return xAuthPlayer != null && xAuthPlayer.isRegistered();
    }

    @Override
    public boolean forceRegister(Player player, final String password) {
        // Folia: use Entity.getScheduler() + CompletableFuture
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(player);
            future.complete(xAuthPlayer != null
                    && xAuthPlugin.getAuthClass(xAuthPlayer).adminRegister(player.getName(), password, null));
        }, null);

        try {
            return future.get(5, TimeUnit.SECONDS) && forceLogin(player);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLog().error("Failed to forceRegister player: {}", player, ex);
            return false;
        } catch (ExecutionException | TimeoutException ex) {
            plugin.getLog().error("Failed to forceRegister player: {}", player, ex);
            return false;
        }
    }
}
