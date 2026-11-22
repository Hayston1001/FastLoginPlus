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

import java.util.regex.Pattern;

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

    private static final Pattern JAVA_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final RequestHandler requestHandler = new RequestHandler();
    private final FastLoginBungee plugin;

    public BungeeAuthHook(FastLoginBungee plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(ProxiedPlayer player) {
        String playerName = player.getName();
        return current(player) && (isAuthenticated(player)
                || requestHandler.forceLogin(playerName) && isAuthenticated(player));
    }

    @Override
    public boolean isRegistered(String playerName) {
        if (!JAVA_NAME.matcher(playerName).matches()) {
            // BungeeAuth 1.4's query does not use parameter binding.
            throw new IllegalArgumentException("Not a valid Java account name");
        }
        return requestHandler.isRegistered(playerName);
    }

    @Override
    public boolean forceRegister(ProxiedPlayer player, String password) {
        return current(player) && !isRegistered(player.getName())
                && requestHandler.forceRegister(player, password) && forceLogin(player);
    }

    @Override
    public boolean isAuthenticated(ProxiedPlayer player) {
        return current(player) && Main.plonline.contains(player.getName());
    }

    private boolean current(ProxiedPlayer player) {
        String name = player.getName();
        return JAVA_NAME.matcher(name).matches() && player.isConnected() && plugin.getProxy().getPlayer(name) == player;
    }
}
