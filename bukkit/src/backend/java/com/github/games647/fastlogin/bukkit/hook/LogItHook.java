/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import io.github.lucaseasedup.logit.CancelledState;
import io.github.lucaseasedup.logit.LogItCore;
import io.github.lucaseasedup.logit.account.Account;
import io.github.lucaseasedup.logit.session.SessionManager;
import org.bukkit.entity.Player;

import java.time.Instant;

/**
 * GitHub: <a href="https://github.com/XziomekX/LogIt">...</a>
 * <p>
 * Project page:
 * <p>
 * Bukkit: Unknown
 * <p>
 * Spigot: Unknown
 */
public class LogItHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    public LogItHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        return AuthPlayerThread.call(plugin, player, () -> login(player));
    }

    @Override
    public boolean isRegistered(String playerName) {
        return activeCore().getAccountManager().isRegistered(playerName);
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return AuthPlayerThread.call(plugin, player, () -> {
            String name = player.getName();
            if (isRegistered(name)) {
                return false;
            }
            Account account = new Account(name);
            account.changePassword(password);
            long now = Instant.now().getEpochSecond();
            account.setLastActiveDate(now);
            account.setRegistrationDate(now);
            return activeCore().getAccountManager().insertAccount(account) == CancelledState.NOT_CANCELLED
                    && isRegistered(name) && login(player);
        });
    }

    private static boolean login(Player player) {
        SessionManager sessions = activeCore().getSessionManager();
        return sessions.isSessionAlive(player) || sessions.startSession(player) == CancelledState.NOT_CANCELLED
                && sessions.isSessionAlive(player);
    }

    private static LogItCore activeCore() {
        LogItCore logIt = LogItCore.getInstance();
        if (!logIt.isStarted()) {
            throw new IllegalStateException("LogIt is not started");
        }
        return logIt;
    }
}
