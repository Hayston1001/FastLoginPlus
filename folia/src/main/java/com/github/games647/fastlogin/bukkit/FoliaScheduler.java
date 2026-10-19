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

/**
 * Folia-compatible scheduler that uses the new regionized scheduler APIs.
 *
 * Folia has no single "main thread". Instead:
 * - Async tasks → Bukkit.getAsyncScheduler()
 * - Global sync tasks (no location) → Bukkit.getGlobalRegionScheduler()
 * - Location-specific sync tasks → Bukkit.getRegionScheduler()
 * - Entity-specific sync tasks → Entity.getScheduler()
 */
public class FoliaScheduler extends AsyncScheduler {

    private final Plugin plugin;
    private final Executor syncExecutor;

    public FoliaScheduler(Plugin plugin, Logger logger) {
        super(logger, command -> Bukkit.getAsyncScheduler().runNow(plugin, task -> command.run()));
        this.plugin = plugin;

        // Sync executor: schedule on the global region (no specific location needed)
        this.syncExecutor = task ->
                Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    /**
     * Returns an executor that runs tasks on the global region thread.
     * Used by command classes for sync operations after async DB calls.
     *
     * @return executor that schedules tasks on the global region
     */
    public Executor getSyncExecutor() {
        return syncExecutor;
    }
}
