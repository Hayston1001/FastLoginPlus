/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.hook;

import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import me.vik1395.BungeeAuth.Main;
import me.vik1395.BungeeAuthAPI.RequestHandler;
import net.md_5.bungee.api.connection.ProxiedPlayer;

/**
 * GitHub:
 * <a href="https://github.com/vik1395/BungeeAuth-Minecraft">...</a>
 * <p>
 * Project page:
 * <p>
 * Spigot:
 * <a href="https://www.spigotmc.org/resources/bungeeauth.493/">...</a>
 */
public class BungeeAuthHook implements AuthPlugin<ProxiedPlayer> {

    private final RequestHandler requestHandler = new RequestHandler();

    public BungeeAuthHook(FastLoginBungee plugin) {
    }

    @Override
    public boolean forceLogin(ProxiedPlayer player) {
        String playerName = player.getName();
        return Main.plonline.contains(playerName) || requestHandler.forceLogin(playerName);
    }

    @Override
    public boolean isRegistered(String playerName) {
        return requestHandler.isRegistered(playerName);
    }

    @Override
    public boolean forceRegister(ProxiedPlayer player, String password) {
        return requestHandler.forceRegister(player, password);
    }
}
