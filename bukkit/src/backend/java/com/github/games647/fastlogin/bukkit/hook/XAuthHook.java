/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import de.luricos.bukkit.xAuth.xAuth;
import de.luricos.bukkit.xAuth.xAuthPlayer;
import org.bukkit.entity.Player;

/**
 * GitHub: <a href="https://github.com/LycanDevelopment/xAuth/">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/xauth/">Bukkit</a>
 */
public class XAuthHook implements AuthPlugin<Player> {

    private final xAuth xAuthPlugin = xAuth.getPlugin();
    private final FastLoginBukkit plugin;

    public XAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        return AuthPlayerThread.call(plugin, player,
                () -> login(xAuthPlugin.getPlayerManager().getPlayer(player)));
    }

    @Override
    public boolean isRegistered(String playerName) {
        //this will load the player if it's not in the cache
        xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(playerName);
        return xAuthPlayer != null && xAuthPlayer.isRegistered();
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return AuthPlayerThread.call(plugin, player, () -> {
            xAuthPlayer account = xAuthPlugin.getPlayerManager().getPlayer(player);
            return account != null && !account.isRegistered()
                    && xAuthPlugin.getAuthClass(account).adminRegister(player.getName(), password, null)
                    && login(xAuthPlugin.getPlayerManager().getPlayer(player));
        });
    }

    private boolean login(xAuthPlayer account) {
        if (account == null || !account.isRegistered()) {
            return false;
        }
        if (account.isAuthenticated()) {
            return true;
        }
        boolean previousPremium = account.isPremium();
        boolean authenticated = false;
        account.setPremium(true);
        try {
            authenticated = xAuthPlugin.getPlayerManager().doLogin(account) && account.isAuthenticated();
            return authenticated;
        } finally {
            if (!authenticated) {
                account.setPremium(previousPremium);
            }
        }
    }
}
