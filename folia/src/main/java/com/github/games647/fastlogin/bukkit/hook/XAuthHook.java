/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
                    plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
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
