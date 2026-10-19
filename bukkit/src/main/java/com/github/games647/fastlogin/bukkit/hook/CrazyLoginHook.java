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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Future;

/**
 * GitHub: <a href="https://github.com/ST-DDT/CrazyLogin">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/server-mods/crazylogin/">Bukkit</a>
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
        //not thread-safe operation
        Future<Optional<LoginPlayerData>> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            LoginPlayerData playerData = crazyLoginPlugin.getPlayerData(player);
            if (playerData != null) {
                //mark the account as logged in
                playerData.setLoggedIn(true);

                String ip = player.getAddress().getAddress().getHostAddress();
//this should be done after login to restore the inventory, show players, prevent potential memory leaks...
//from: https://github.com/ST-DDT/CrazyLogin/blob/master/src/main/java/de/st_ddt/crazylogin/CrazyLogin.java#L1948
                playerData.resetLoginFails();
                player.setFireTicks(0);

                if (playerListener != null) {
                    playerListener.removeMovementBlocker(player);
                    playerListener.disableHidenInventory(player);
                    playerListener.disableSaveLogin(player);
                    playerListener.unhidePlayer(player);
                }

//loginFailuresPerIP.remove(IP);
//illegalCommandUsesPerIP.remove(IP);
//tempBans.remove(IP);
                playerData.addIP(ip);
                player.setMetadata("Authenticated", new Authenticated(crazyLoginPlugin, player));
                crazyLoginPlugin.unregisterDynamicHooks();
                return Optional.of(playerData);
            }

            return Optional.empty();
        });

        try {
            // bound the wait — a stuck main thread must not pile up
            // unbounded async login threads
            Optional<LoginPlayerData> result = future.get(5, TimeUnit.SECONDS)
                    .filter(LoginPlayerData::isLoggedIn);
            if (result.isPresent()) {
                //SQL-Queries should run async
                crazyLoginPlugin.getCrazyDatabase().saveWithoutPassword(result.get());
                return true;
            }
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            plugin.getLog().error("Failed to forceLogin player: {}", player, ex);
            return false;
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

        //this executes a sql query and accesses only thread safe collections, so we can run it async
        LoginPlayerData playerData = crazyLoginPlugin.getPlayerData(player.getName());
        if (playerData == null) {
            //create a fake account - this will be saved to the database with the password=FAILEDLOADING
            //user cannot log in with that password unless the admin uses plain text
            //this automatically marks the player as logged in
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
