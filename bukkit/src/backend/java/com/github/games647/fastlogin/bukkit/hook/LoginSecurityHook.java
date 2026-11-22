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
import com.lenis0012.bukkit.loginsecurity.session.SessionManager;
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
    private final SessionManager sessions;

    public LoginSecurityHook(FastLoginBukkit plugin) {
        this(plugin, LoginSecurity.getSessionManager());
    }

    LoginSecurityHook(FastLoginBukkit plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = java.util.Objects.requireNonNull(sessions, "LoginSecurity sessions unavailable");
    }

    @Override
    public boolean forceLogin(Player player) {
        PlayerSession session = sessions.getPlayerSession(player);
        if (!current(session, player)) {
            return false;
        }
        return session.isLoggedIn()
                || session.performAction(new LoginAction(AuthService.PLUGIN, plugin)).isSuccess()
                && current(session, player) && session.isLoggedIn();
    }

    @Override
    public boolean isRegistered(String playerName) {
        PlayerSession session = sessions.getOfflineSession(playerName);
        if (session == null) {
            throw new IllegalStateException("LoginSecurity could not resolve the account for " + playerName);
        }
        return session.isRegistered();
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        PlayerSession session = sessions.getPlayerSession(player);
        return current(session, player) && !session.isRegistered()
                && session.performAction(new RegisterAction(AuthService.PLUGIN, plugin, password)).isSuccess()
                && current(session, player) && session.isLoggedIn();
    }

    private boolean current(PlayerSession session, Player player) {
        // LoginSecurity resolves the action target from its session's last name.
        return plugin.isEnabled() && session != null && player.isOnline() && session.getPlayer() == player;
    }
}
