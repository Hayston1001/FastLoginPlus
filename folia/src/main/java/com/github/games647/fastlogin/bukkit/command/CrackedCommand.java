/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.bukkit.command;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;

public class CrackedCommand extends ToggleCommand {

    private static final String PERM_PREFIX = "fastloginplus.folia.command.";

    public CrackedCommand(FastLoginBukkit plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            onCrackedSelf(sender);
        } else {
            onCrackedOther(sender, command, args);
        }

        return true;
    }

    private void onCrackedSelf(CommandSender sender) {
        if (isConsole(sender)) {
            return;
        }

        if (!sender.hasPermission(PERM_PREFIX + "cracked")) {
            plugin.getCore().sendLocaleMessage("no-permission", sender);
            return;
        }

        Player player = (Player) sender;
        String playerName = sender.getName();

        // Always clear AuthMe locally — AuthMe is on the backend,
        // the proxy (BungeeCord/Velocity) cannot access it.
        // This must run even when the command is forwarded to a proxy.
        AuthMePremiumIntegrator integrator = plugin.getAuthMePremiumIntegrator();
        if (integrator != null) {
            integrator.clearPlayerPremium(playerName);
        } else {
            plugin.getLog().warn("CrackedCommand: authMePremiumIntegrator is null, "
                + "skipping AuthMe cleanup for {}", playerName);
        }

        // Forward to proxy if enabled; proxy handles profile persistence,
        // messages, kick — the backend has no DB in proxy mode.
        if (forwardCrackedCommand(sender, playerName)) {
            return;
        }

        // Local path (no proxy): load profile from local DB
        // 0.5.0/F011: database calls must not run on the main thread
        plugin.getScheduler().runAsync(() -> {
            StoredProfile profile = plugin.getCore().getStorage().loadProfile(playerName);
            if (profile == null) {
                // null only on SQL exception (lock timeout, DB down) — the database failed
                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("database-error", sender));
                return;
            }
            if (!profile.isOnlinemodePreferred()) {
                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("not-premium", sender));
                return;
            }

            plugin.getScheduler().getSyncExecutor().execute(() ->
                    plugin.getCore().sendLocaleMessage("remove-premium", sender));

            profile.setOnlinemodePreferred(false);
            profile.setId(null);

            plugin.getCore().getStorage().save(profile);

            // Local path (no proxy): event + kick
            plugin.getServer().getPluginManager().callEvent(
                    new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER)
            );

            plugin.getScheduler().getSyncExecutor().execute(() -> {
                if (plugin.getCore().getConfig().getBoolean("kick-toggle")) {
                    player.kickPlayer(plugin.getCore().getMessage("remove-premium"));
                } else {
                    // 0.5.0/F012: this is the *cracked* self path — the message
                    // was misleadingly the add-premium key
                    plugin.getCore().sendLocaleMessage("remove-premium", sender);
                }
            });
        });
    }

    private void onCrackedOther(CommandSender sender, Command command, String[] args) {
        if (!hasOtherPermission(sender, PERM_PREFIX + "cracked")) {
            return;
        }

        String playerName = args[0];

        // Always clear AuthMe locally — same as onCrackedSelf().
        // AuthMe is on the backend, the proxy cannot access it.
        AuthMePremiumIntegrator integrator = plugin.getAuthMePremiumIntegrator();
        if (integrator != null) {
            integrator.clearPlayerPremium(playerName);
        } else if (plugin.getCore().isDebug()) {
            plugin.getLog().info("CrackedCommand: authMePremiumIntegrator is null, "
                + "skipping AuthMe cleanup for {}", playerName);
        }

        // Forward to proxy if enabled; proxy handles profile persistence,
        // messages, kick — the backend has no DB in proxy mode.
        if (forwardCrackedCommand(sender, playerName)) {
            return;
        }

        // Local path (no proxy): load profile from local DB
        // 0.5.0/F011: database calls must not run on the main thread
        plugin.getScheduler().runAsync(() -> {
            StoredProfile profile = plugin.getCore().getStorage().loadProfile(playerName);
            if (profile == null) {
                // null only on SQL exception (lock timeout, DB down) — the database failed
                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("database-error", sender));
                return;
            }

            //existing player is already cracked
            if (profile.isExistingPlayer() && !profile.isOnlinemodePreferred()) {
                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("not-premium-other", sender));
                return;
            }

            plugin.getScheduler().getSyncExecutor().execute(() ->
                    plugin.getCore().sendLocaleMessage("remove-premium-other", sender));

            profile.setOnlinemodePreferred(false);
            profile.setId(null);

            plugin.getCore().getStorage().save(profile);

            // Local path (no proxy): event
            plugin.getServer().getPluginManager().callEvent(
                    new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER));
        });
    }

    private boolean forwardCrackedCommand(CommandSender sender, String target) {
        return forwardBungeeCommand(sender, target, false);
    }
}
