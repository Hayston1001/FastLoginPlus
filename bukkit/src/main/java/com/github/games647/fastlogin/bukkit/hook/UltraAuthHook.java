/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ultraauth.api.UltraAuthAPI;
import ultraauth.managers.PlayerManager;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Future;

/**
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/ultraauth-aa/">Bukkit</a>
 * <p>
 * <a href="https://www.spigotmc.org/resources/ultraauth.17044/">Spigot</a>
 */
public class UltraAuthHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    public UltraAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        //not thread-safe
        Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            if (UltraAuthAPI.isAuthenticated(player)) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
                }
                return false;
            }

            UltraAuthAPI.authenticatedPlayer(player);
            return UltraAuthAPI.isAuthenticated(player);
        });

        try {
            // bound the wait — a stuck main thread must not pile up
            // unbounded async login threads
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
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
        //the register method silents any exception so check if our entry was saved
        return PlayerManager.getInstance().checkPlayerPassword(player, password) && forceLogin(player);
    }
}
