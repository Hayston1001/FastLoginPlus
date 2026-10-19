/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.lenis0012.bukkit.loginsecurity.LoginSecurity;
import com.lenis0012.bukkit.loginsecurity.session.AuthService;
import com.lenis0012.bukkit.loginsecurity.session.PlayerSession;
import com.lenis0012.bukkit.loginsecurity.session.action.LoginAction;
import com.lenis0012.bukkit.loginsecurity.session.action.RegisterAction;
import org.bukkit.entity.Player;

/**
 * GitHub: <a href="https://github.com/lenis0012/LoginSecurity-2">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/loginsecurity/">Bukkit</a>
 * <p>
 * <a href="https://www.spigotmc.org/resources/loginsecurity.19362/">Spigot</a>
 */
public class LoginSecurityHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    public LoginSecurityHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        PlayerSession session = LoginSecurity.getSessionManager().getPlayerSession(player);
        if (session.isAuthorized()) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
            }
            return true;
        }

        return session.isAuthorized()
                || session.performAction(new LoginAction(AuthService.PLUGIN, plugin)).isSuccess();
    }

    @Override
    public boolean isRegistered(String playerName) {
        PlayerSession session = LoginSecurity.getSessionManager().getOfflineSession(playerName);
        return session.isRegistered();
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        PlayerSession session = LoginSecurity.getSessionManager().getPlayerSession(player);
        return session.performAction(new RegisterAction(AuthService.PLUGIN, plugin, password)).isSuccess();
    }
}
