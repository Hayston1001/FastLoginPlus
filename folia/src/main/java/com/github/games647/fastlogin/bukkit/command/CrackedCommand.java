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

        Player player = (Player) sender;
        String playerName = sender.getName();

        // todo: load async if
        StoredProfile profile = plugin.getCore().getStorage().loadProfile(playerName);
        if (!profile.isOnlinemodePreferred()) {
            plugin.getCore().sendLocaleMessage("not-premium", sender);
            return;
        }

        plugin.getCore().sendLocaleMessage("remove-premium", sender);

        profile.setOnlinemodePreferred(false);
        profile.setId(null);

        // Always clear AuthMe locally — AuthMe is on the backend,
        // the proxy (BungeeCord/Velocity) cannot access it.
        // This must run even when the command is forwarded to a proxy.
        AuthMePremiumIntegrator integrator = plugin.getAuthMePremiumIntegrator();
        if (integrator != null) {
            integrator.clearPlayerPremium(playerName);
        } else {
            plugin.getLog().warn("[FLP] CrackedCommand: authMePremiumIntegrator is null, "
                + "skipping AuthMe cleanup for {}", playerName);
        }

        // Forward to proxy if enabled; proxy handles profile persistence + kick
        if (forwardCrackedCommand(sender, playerName)) {
            return;
        }

        // Local path (no proxy): save profile + kick
        plugin.getScheduler().runAsync(() -> {
            plugin.getCore().getStorage().save(profile);

            plugin.getServer().getPluginManager().callEvent(
                    new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER)
            );

            plugin.getScheduler().getSyncExecutor().execute(() -> {
                if (plugin.getCore().getConfig().getBoolean("kick-toggle")) {
                    player.kickPlayer(plugin.getCore().getMessage("remove-premium"));
                } else {
                    plugin.getCore().sendLocaleMessage("add-premium", sender);
                }
            });
        });
    }

    private void onCrackedOther(CommandSender sender, Command command, String[] args) {
        if (!hasOtherPermission(sender, command)) {
            return;
        }

        if (forwardCrackedCommand(sender, args[0])) {
            return;
        }

        //todo: load async
        StoredProfile profile = plugin.getCore().getStorage().loadProfile(args[0]);
        if (profile == null) {
            sender.sendMessage("Error occurred");
            return;
        }

        //existing player is already cracked
        if (profile.isExistingPlayer() && !profile.isOnlinemodePreferred()) {
            plugin.getCore().sendLocaleMessage("not-premium-other", sender);
        } else {
            plugin.getCore().sendLocaleMessage("remove-premium-other", sender);

            profile.setOnlinemodePreferred(false);
            plugin.getScheduler().runAsync(() -> {
                plugin.getCore().getStorage().save(profile);
                plugin.getServer().getPluginManager().callEvent(
                        new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER));
            });
        }
    }

    private boolean forwardCrackedCommand(CommandSender sender, String target) {
        return forwardBungeeCommand(sender, target, false);
    }
}
