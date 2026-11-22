/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.rabbitcomapny.Passky;
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
        return AuthPlayerThread.call(plugin, player, () -> {
            requireReadableStore();
            Identifier id = new Identifier(player);
            if (!PasskyAPI.isRegistered(id)) {
                return false;
            }
            if (PasskyAPI.isLoggedIn(id)) {
                return true;
            }
            LoginResult result = PasskyAPI.forceLogin(id, true);
            return result.success && result.player == player && PasskyAPI.isLoggedIn(id);
        });
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return AuthPlayerThread.call(plugin, player, () -> {
            requireReadableStore();
            Identifier id = new Identifier(player);
            RegisterResult result = PasskyAPI.forceRegister(id, password, true);
            return result.success && result.player == player && PasskyAPI.isRegistered(id)
                    && PasskyAPI.isLoggedIn(id);
        });
    }

    @Override
    public boolean isRegistered(String playerName) {
        requireReadableStore();
        return PasskyAPI.isRegistered(new Identifier(playerName));
    }

    private static void requireReadableStore() {
        // The v3 API masks SQL errors as an account result. Name-only lookups
        // in UUID mode may also resolve a different offline profile.
        if (Passky.getInstance().getConf().getBoolean("mysql")
                || Passky.getInstance().getConf().getInt("player_identifier") != 0) {
            throw new IllegalStateException("Passky automatic authentication requires file storage and name mode");
        }
    }
}
