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
        return AuthPlayerThread.call(plugin, player, () -> login(player));
    }

    @Override
    public boolean isRegistered(String playerName) {
        return UltraAuthAPI.isRegisterd(playerName);
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return AuthPlayerThread.call(plugin, player, () -> {
            if (UltraAuthAPI.isRegisterd(player)) {
                return false;
            }
            UltraAuthAPI.setPlayerPasswordOnline(player, password);
            // The setter does not report write failures.
            return PlayerManager.getInstance().checkPlayerPassword(player, password) && login(player);
        });
    }

    private static boolean login(Player player) {
        if (!UltraAuthAPI.isAuthenticated(player)) {
            UltraAuthAPI.authenticatedPlayer(player);
        }
        return UltraAuthAPI.isAuthenticated(player);
    }
}
