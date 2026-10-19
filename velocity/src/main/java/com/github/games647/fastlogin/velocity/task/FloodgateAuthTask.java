/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateManagement;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;

public class FloodgateAuthTask
        extends FloodgateManagement<Player, CommandSource, VelocityLoginSession, FastLoginVelocity> {

    private final RegisteredServer server;

    public FloodgateAuthTask(FastLoginCore<Player, CommandSource, FastLoginVelocity> core, Player player,
                             FloodgatePlayer floodgatePlayer, RegisteredServer server) {
        super(core, player, floodgatePlayer);
        this.server = server;
    }

    @Override
    protected void startLogin() {
        VelocityLoginSession session = new VelocityLoginSession(player.getUsername(), isRegistered, profile);
        core.getPlugin().getSession().put(player.getRemoteAddress(), session);

        // enable auto login based on the value of 'autoLoginFloodgate' in config.yml
        boolean forcedOnlineMode = autoLoginFloodgate.equals("true")
                || (autoLoginFloodgate.equals("linked") && isLinked);

        // delay sending force command, because Paper will process the login event asynchronously
        // In this case it means that the force command (plugin message) is already received and processed while
        // player is still in the login phase and reported to be offline.
        Runnable loginTask = new ForceLoginTask(core.getPlugin().getCore(), player, server, session, forcedOnlineMode);
        core.getPlugin().getScheduler().runAsyncDelayed(loginTask, Duration.ofSeconds(1));
    }

    @Override
    protected String getName(Player player) {
        return player.getUsername();
    }

    @Override
    protected UUID getUUID(Player player) {
        return player.getUniqueId();
    }

    @Override
    protected InetSocketAddress getAddress(Player player) {
        return player.getRemoteAddress();
    }
}
