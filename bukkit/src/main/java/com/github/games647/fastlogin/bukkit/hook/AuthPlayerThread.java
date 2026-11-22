/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class AuthPlayerThread {

    private AuthPlayerThread() {
    }

    static boolean call(FastLoginBukkit plugin, Player player, BooleanSupplier operation) {
        FutureTask<Boolean> action = new FutureTask<>(() -> current(plugin, player)
                && operation.getAsBoolean() && current(plugin, player));
        try {
            if (Bukkit.isPrimaryThread()) {
                action.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, action);
            }
            return action.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            plugin.getLog().warn("Auth plugin action not confirmed for {} ({})",
                    player.getName(), failure.getClass().getSimpleName());
        } finally {
            action.cancel(false);
        }
        return false;
    }

    private static boolean current(FastLoginBukkit plugin, Player player) {
        return plugin.isEnabled() && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }
}
