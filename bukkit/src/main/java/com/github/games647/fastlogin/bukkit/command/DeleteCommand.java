/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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

    private static final String PERM_PREFIX = "fastloginplus.bukkit.command.";

    protected final FastLoginBukkit plugin;

    public DeleteCommand(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String[] args) {
        if (!sender.hasPermission(PERM_PREFIX + "delete")) {
            plugin.getCore().sendLocaleMessage("no-permission", sender);
            return true;
        }

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

        // database calls must not run on the main thread — the
        // whole load/validate/delete flow runs async, replies hop back to the
        // main thread
        plugin.getScheduler().runAsync(() -> {
            StoredProfile profile = plugin.getCore().getStorage().loadProfile(targetName);
            if (profile == null) {
                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("database-error", sender));
                return;
            }

            if (!profile.isExistingPlayer()) {
                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("delete-not-found", sender));
                return;
            }

            if (profile.isOnlinemodePreferred()) {
                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("delete-premium-denied", sender));
                return;
            }

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
        if (sender instanceof org.bukkit.plugin.messaging.PluginMessageRecipient) {
            // player invoked — the invoker is the message carrier
            plugin.getBungeeManager().sendPluginMessage(
                    (org.bukkit.plugin.messaging.PluginMessageRecipient) sender,
                    new DeletePremiumMessage(targetName, true));
        } else {
            java.util.Optional<? extends org.bukkit.entity.Player> optPlayer =
                    plugin.getServer().getOnlinePlayers().stream().findFirst();
            if (!optPlayer.isPresent()) {
                plugin.getLog().info("No player online to relay delete message — "
                    + "queuing pending delete for {}", targetName);
                if (plugin.getPendingRelayStore().queueDelete(targetName)) {
                    // retry task only for a newly queued entry — one is already
                    // running for an entry that is waiting
                    plugin.scheduleDeleteRelay(targetName);
                }
                return;
            }
            plugin.getBungeeManager().sendPluginMessage(optPlayer.get(),
                    new DeletePremiumMessage(targetName, false));
        }
    }
}
