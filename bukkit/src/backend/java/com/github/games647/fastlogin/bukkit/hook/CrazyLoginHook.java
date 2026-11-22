/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import de.st_ddt.crazylogin.CrazyLogin;
import de.st_ddt.crazylogin.data.LoginPlayerData;
import de.st_ddt.crazylogin.listener.PlayerListener;
import de.st_ddt.crazylogin.metadata.Authenticated;
import de.st_ddt.crazyplugin.exceptions.CrazyException;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

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
        return AuthPlayerThread.call(plugin, player, () -> {
            InetSocketAddress address = player.getAddress();
            if (address == null || address.getAddress() == null || crazyLoginPlugin.getCrazyDatabase() == null) {
                return false;
            }
            LoginPlayerData playerData = crazyLoginPlugin.getPlayerData(player);
            if (playerData == null) {
                return false;
            }

            if (crazyLoginPlugin.isLoggedIn(player)) {
                return true;
            }
            playerData.resetLoginFails();
            player.setFireTicks(0);
            if (playerListener != null) {
                playerListener.removeMovementBlocker(player);
                playerListener.disableHidenInventory(player);
                playerListener.disableSaveLogin(player);
                playerListener.unhidePlayer(player);
            }
            playerData.addIP(address.getAddress().getHostAddress());
            crazyLoginPlugin.getCrazyDatabase().saveWithoutPassword(playerData);
            player.setMetadata("Authenticated", new Authenticated(crazyLoginPlugin, player));
            playerData.setLoggedIn(true);
            crazyLoginPlugin.unregisterDynamicHooks();
            return crazyLoginPlugin.isLoggedIn(player);
        });
    }

    @Override
    public boolean isRegistered(String playerName) {
        if (crazyLoginPlugin.getCrazyDatabase() == null) {
            throw new IllegalStateException("CrazyLogin account store is not available");
        }
        return crazyLoginPlugin.getPlayerData(playerName) != null;
    }

    @Override
    public boolean notifyGeneratedPassword() {
        return false;
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return AuthPlayerThread.call(plugin, player, () -> {
            if (isRegistered(player.getName())) {
                return false;
            }
            try {
                // Use the plugin's normal registration rules and credential delivery.
                crazyLoginPlugin.playerPassword(player, password);
            } catch (CrazyException refused) {
                return false;
            }
            LoginPlayerData data = crazyLoginPlugin.getPlayerData(player.getName());
            return data != null && data.isPassword(password) && crazyLoginPlugin.isLoggedIn(player);
        });
    }

    protected PlayerListener getListener() {
        try {
            return (PlayerListener) resolveListenerField(crazyLoginPlugin.getClass()).get(crazyLoginPlugin);
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalStateException("Cannot access CrazyLogin's player listener", unavailable);
        }
    }

    static Field resolveListenerField(Class<?> pluginClass) throws NoSuchFieldException {
        for (Class<?> type = pluginClass; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == PlayerListener.class) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException(PlayerListener.class.getName());
    }
}
