/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import java.util.concurrent.Executor;

public class BukkitScheduler extends AsyncScheduler {

    private final Executor syncExecutor;

    public BukkitScheduler(Plugin plugin, Logger logger) {
        super(logger, command -> Bukkit.getScheduler().runTaskAsynchronously(plugin, command));

        syncExecutor = task -> Bukkit.getScheduler().runTask(plugin, task);
    }

    public Executor getSyncExecutor() {
        return syncExecutor;
    }
}
