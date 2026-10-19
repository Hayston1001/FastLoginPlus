/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.task;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateManagement;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.net.InetSocketAddress;
import java.util.UUID;

public class FloodgateAuthTask
        extends FloodgateManagement<ProxiedPlayer, CommandSender, BungeeLoginSession, FastLoginBungee> {

    private final Server server;

    public FloodgateAuthTask(FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core, ProxiedPlayer player,
            FloodgatePlayer floodgatePlayer, Server server) {
        super(core, player, floodgatePlayer);
        this.server = server;
    }

    @Override
    protected void startLogin() {
        BungeeLoginSession session = new BungeeLoginSession(player.getName(), isRegistered, profile);
        core.getPlugin().getSession().put(player.getPendingConnection(), session);

        // run login task
        Runnable forceLoginTask = new ForceLoginTask(core.getPlugin().getCore(), player, server, session,
                isAutoAuthAllowed(autoLoginFloodgate));
        core.getPlugin().getScheduler().runAsync(forceLoginTask);
    }

    @Override
    protected String getName(ProxiedPlayer player) {
        return player.getName();
    }

    @Override
    protected UUID getUUID(ProxiedPlayer player) {
        return player.getUniqueId();
    }

    @Override
    protected InetSocketAddress getAddress(ProxiedPlayer player) {
        return player.getAddress();
    }

}
