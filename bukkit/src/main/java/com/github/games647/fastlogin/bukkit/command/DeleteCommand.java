/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.core.message.DeletePremiumMessage;
import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;
import com.github.games647.fastlogin.core.storage.StoredProfile;

public class DeleteCommand implements CommandExecutor {

    protected final FastLoginBukkit plugin;

    public DeleteCommand(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            plugin.getCore().sendLocaleMessage("delete-specify-player", sender);
            return true;
        }

        String targetName = args[0];

        if (plugin.getBungeeManager().isEnabled()) {
            sendBungeeDeleteMessage(sender, targetName);
            plugin.getCore().sendLocaleMessage("wait-on-proxy", sender);
            return true;
        }

        StoredProfile profile = plugin.getCore().getStorage().loadProfile(targetName);
        if (profile == null) {
            sender.sendMessage("Error occurred");
            return true;
        }

        if (!profile.isExistingPlayer()) {
            plugin.getCore().sendLocaleMessage("delete-not-found", sender);
            return true;
        }

        if (profile.isOnlinemodePreferred()) {
            plugin.getCore().sendLocaleMessage("delete-premium-denied", sender);
            return true;
        }

        plugin.getScheduler().runAsync(() -> {
            boolean deleted = plugin.getCore().getStorage().deleteProfile(targetName);
            if (deleted) {
                plugin.getServer().getPluginManager().callEvent(
                        new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER)
                );
            }
            plugin.getScheduler().getSyncExecutor().execute(() -> {
                if (deleted) {
                    plugin.getCore().sendLocaleMessage("delete-success", sender);
                } else {
                    plugin.getCore().sendLocaleMessage("delete-fail", sender);
                }
            });
        });

        return true;
    }

    private void sendBungeeDeleteMessage(CommandSender sender, String targetName) {
        DeletePremiumMessage message = new DeletePremiumMessage(targetName);
        if (sender instanceof org.bukkit.plugin.messaging.PluginMessageRecipient) {
            plugin.getBungeeManager().sendPluginMessage(
                    (org.bukkit.plugin.messaging.PluginMessageRecipient) sender, message
            );
        } else {
            java.util.Optional<? extends org.bukkit.entity.Player> optPlayer =
                    plugin.getServer().getOnlinePlayers().stream().findFirst();
            if (!optPlayer.isPresent()) {
                plugin.getLog().info("No player online to send a plugin message to the proxy");
                return;
            }
            plugin.getBungeeManager().sendPluginMessage(optPlayer.get(), message);
        }
    }
}
