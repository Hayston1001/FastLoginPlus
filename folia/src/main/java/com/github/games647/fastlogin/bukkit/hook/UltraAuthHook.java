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
