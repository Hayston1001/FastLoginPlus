/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.rabbitcomapny.api.Identifier;
import com.rabbitcomapny.api.LoginResult;
import com.rabbitcomapny.api.PasskyAPI;
import com.rabbitcomapny.api.RegisterResult;
import org.bukkit.entity.Player;

public class PasskyHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    public PasskyHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        LoginResult result = PasskyAPI.forceLogin(new Identifier(player), true);
        if (!result.success) {
            plugin.getLog().error("Failed to force login {} via Passky: {}", player.getName(), result.status);
        }

        return result.success;
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        RegisterResult result = PasskyAPI.forceRegister(new Identifier(player), password, true);
        if (!result.success) {
            plugin.getLog().error("Failed to register {} via Passky: {}", player.getName(), result.status);
        }

        return result.success;
    }

    @Override
    public boolean isRegistered(String playerName) {
        return PasskyAPI.isRegistered(new Identifier(playerName));
    }
}
