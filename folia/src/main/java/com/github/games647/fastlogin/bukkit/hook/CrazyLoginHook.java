/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import de.st_ddt.crazylogin.CrazyLogin;
import de.st_ddt.crazylogin.data.LoginPlayerData;
import de.st_ddt.crazylogin.databases.CrazyLoginDataDatabase;
import de.st_ddt.crazylogin.listener.PlayerListener;
import de.st_ddt.crazylogin.metadata.Authenticated;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Folia-adapted CrazyLoginHook.
 *
 * Uses Entity.getScheduler() + CompletableFuture instead of
 * Bukkit.getScheduler().callSyncMethod().
 */
public class CrazyLoginHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    private final CrazyLogin crazyLoginPlugin;
    private final PlayerListener playerListener;

    public CrazyLoginHook(FastLoginBukkit plugin) {
        this.plugin = plugin;

        crazyLoginPlugin = CrazyLogin.getPlugin();
        playerListener = getListener();
    }

    @Override
    public boolean forceLogin(Player player) {
        // Folia: use Entity.getScheduler() + CompletableFuture
        CompletableFuture<Optional<LoginPlayerData>> future = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            LoginPlayerData playerData = crazyLoginPlugin.getPlayerData(player);
            if (playerData != null) {
                playerData.setLoggedIn(true);

                String ip = player.getAddress().getAddress().getHostAddress();
                playerData.resetLoginFails();
                player.setFireTicks(0);

                if (playerListener != null) {
                    playerListener.removeMovementBlocker(player);
                    playerListener.disableHidenInventory(player);
                    playerListener.disableSaveLogin(player);
                    playerListener.unhidePlayer(player);
                }

                playerData.addIP(ip);
                player.setMetadata("Authenticated", new Authenticated(crazyLoginPlugin, player));
                crazyLoginPlugin.unregisterDynamicHooks();
                future.complete(Optional.of(playerData));
                return;
            }

            future.complete(Optional.empty());
        }, null);

        try {
            Optional<LoginPlayerData> result = future.get(5, TimeUnit.SECONDS).filter(LoginPlayerData::isLoggedIn);
            if (result.isPresent()) {
                crazyLoginPlugin.getCrazyDatabase().saveWithoutPassword(result.get());
                return true;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLog().error("Failed to forceLogin player: {}", player, ex);
        } catch (ExecutionException | TimeoutException ex) {
            plugin.getLog().error("Failed to forceLogin player: {}", player, ex);
        }

        return false;
    }

    @Override
    public boolean isRegistered(String playerName) {
        return crazyLoginPlugin.getPlayerData(playerName) != null;
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        CrazyLoginDataDatabase crazyDatabase = crazyLoginPlugin.getCrazyDatabase();

        LoginPlayerData playerData = crazyLoginPlugin.getPlayerData(player.getName());
        if (playerData == null) {
            crazyDatabase.save(new LoginPlayerData(player));
            return forceLogin(player);
        }

        return false;
    }

    protected PlayerListener getListener() {
        FieldAccessor accessor = Accessors.getFieldAccessor(crazyLoginPlugin.getClass(), PlayerListener.class, true);
        return (PlayerListener) accessor.get(crazyLoginPlugin);
    }
}
