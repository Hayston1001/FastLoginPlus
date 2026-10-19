/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.task;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateManagement;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Folia-adapted FloodgateAuthTask.
 *
 * Uses Bukkit.getAsyncScheduler() instead of Bukkit.getScheduler().runTaskAsynchronously().
 */
public class FloodgateAuthTask extends FloodgateManagement<Player, CommandSender, BukkitLoginSession, FastLoginBukkit> {

    public FloodgateAuthTask(FastLoginCore<Player, CommandSender, FastLoginBukkit> core, Player player,
                             FloodgatePlayer floodgatePlayer) {
        super(core, player, floodgatePlayer);
    }

    @Override
    protected void startLogin() {
        BukkitLoginSession session = new BukkitLoginSession(player.getName(), isRegistered, profile);

        session.setVerifiedPremium(isAutoAuthAllowed(autoLoginFloodgate));

        Runnable forceLoginTask = new ForceLoginTask(core.getPlugin().getCore(), player, session);
        // Folia: use AsyncScheduler
        Bukkit.getAsyncScheduler().runNow(core.getPlugin(), task -> forceLoginTask.run());
    }

    protected String getName(Player player) {
        return player.getName();
    }

    protected UUID getUUID(Player player) {
        return player.getUniqueId();
    }

    protected InetSocketAddress getAddress(Player player) {
        return player.getAddress();
    }

}
