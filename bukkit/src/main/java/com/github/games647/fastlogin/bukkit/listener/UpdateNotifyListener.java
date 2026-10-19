/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.UpdateChecker;
import com.github.games647.fastlogin.core.shared.FastLoginCore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Notifies OP players about available updates when they log in.
 */
public class UpdateNotifyListener implements Listener {

    private final FastLoginBukkit plugin;

    public UpdateNotifyListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().isOp()) {
            return;
        }

        FastLoginCore<?, ?, ?> core = plugin.getCore();
        UpdateChecker checker = core.getUpdateChecker();
        if (checker == null || checker.getLatestVersion() == null) {
            return;
        }

        String msg = core.getMessage("update-available");
        if (msg != null && !msg.isEmpty()) {
            event.getPlayer().sendMessage(
                    msg.replace("%new%", checker.getLatestVersion())
                       .replace("%current%", checker.getCurrentVersion()));
        }
    }
}
