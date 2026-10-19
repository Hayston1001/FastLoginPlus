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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Future;

/**
 * GitHub: <a href="https://github.com/LycanDevelopment/xAuth/">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/xauth/">Bukkit</a>
 */
public class XAuthHook implements AuthPlugin<Player> {

    private final xAuth xAuthPlugin = xAuth.getPlugin();
    private final FastLoginBukkit plugin;

    public XAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        //not thread-safe
        Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(player);
            if (xAuthPlayer != null) {
                if (xAuthPlayer.isAuthenticated()) {
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
                    }
                    return false;
                }

                //we checked that the player is premium (paid account)
                xAuthPlayer.setPremium(true);

                //unprotect the inventory, op status...
                return xAuthPlugin.getPlayerManager().doLogin(xAuthPlayer);
            }

            return false;
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
        //this will load the player if it's not in the cache
        xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(playerName);
        return xAuthPlayer != null && xAuthPlayer.isRegistered();
    }

    @Override
    public boolean forceRegister(Player player, final String password) {
        //not thread-safe
        Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(xAuthPlugin, () -> {
            xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(player);
            //this should run async because the plugin executes a sql query, but the method
            //accesses non thread-safe collections :(
            return xAuthPlayer != null
                    && xAuthPlugin.getAuthClass(xAuthPlayer).adminRegister(player.getName(), password, null);

        });

        try {
            //login in the player after registration
            // bound the wait (see forceLogin)
            return future.get(5, TimeUnit.SECONDS) && forceLogin(player);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            plugin.getLog().error("Failed to forceRegister player: {}", player, ex);
            return false;
        }
    }
}
