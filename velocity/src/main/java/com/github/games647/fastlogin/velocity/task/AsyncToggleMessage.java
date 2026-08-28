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
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.core.message.ToggleFeedbackMessage;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginPremiumToggleEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;

public class AsyncToggleMessage implements Runnable {

    private final FastLoginCore<Player, CommandSource, FastLoginVelocity> core;
    private final Player sender;
    private final String senderName;
    private final String targetPlayer;
    private final boolean toPremium;
    private final boolean isPlayerSender;

    public AsyncToggleMessage(FastLoginCore<Player, CommandSource, FastLoginVelocity> core,
                              Player sender, String playerName, boolean toPremium, boolean playerSender) {
        this.core = core;
        this.sender = sender;
        this.targetPlayer = playerName;
        this.toPremium = toPremium;
        this.isPlayerSender = playerSender;
        this.senderName = sender.getUsername();
    }

    @Override
    public void run() {
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Proxy toggle task: target={} toPremium={} isPlayerSender={} invoker={}",
                    targetPlayer, toPremium, isPlayerSender, senderName);
        }
        if (toPremium) {
            activatePremium();
        } else {
            turnOffPremium();
        }
    }

    private void turnOffPremium() {
        StoredProfile playerProfile = core.getStorage().loadProfile(targetPlayer);
        if (playerProfile == null) {
            // null only on SQL exception — abort instead of NPE, give the invoker feedback
            core.getPlugin().getLog().warn("Cannot toggle premium state for {}: database query failed",
                    targetPlayer);
            sendMessage("database-error");
            return;
        }
        //existing player is already cracked
        if (playerProfile.isExistingPlayer() && !playerProfile.isOnlinemodePreferred()) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("{} is already cracked; skipping toggle", targetPlayer);
            }
            boolean isSelf = senderName.equalsIgnoreCase(targetPlayer);
            sendMessage(isSelf ? "not-premium" : "not-premium-other");
            // No state change → no kick.  The player's current session already
            // matches the database; kicking here would ignore a kick-toggle:false
            // setting and used the misleading "premium removed" text.  Aligned
            // with activatePremium()'s already-premium skip (no kick there).
            return;
        }

        playerProfile.setOnlinemodePreferred(false);
        playerProfile.setId(null);
        core.getStorage().save(playerProfile);
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Marked {} as cracked in proxy database", targetPlayer);
        }
        PremiumToggleReason reason = (!isPlayerSender || !senderName.equalsIgnoreCase(playerProfile.getName()))
            ? PremiumToggleReason.COMMAND_OTHER : PremiumToggleReason.COMMAND_SELF;
        core.getPlugin().getProxy().getEventManager().fire(
            new VelocityFastLoginPremiumToggleEvent(playerProfile, reason));

        boolean isSelf = senderName.equalsIgnoreCase(targetPlayer);
        sendMessage(isSelf ? "remove-premium" : "remove-premium-other");

        // Kick the target player so they reconnect with the updated profile
        if (core.getConfig().getBoolean("kick-toggle")) {
            Optional<Player> target = core.getPlugin().getProxy().getPlayer(targetPlayer);
            if (target.isPresent()) {
                if (core.isDebug()) {
                    core.getPlugin().getLog().info("Kicking {} to apply cracked profile (kick-toggle)",
                            targetPlayer);
                }
                TextComponent msg = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(core.getMessage("remove-premium"));
                target.get().disconnect(msg);
            }
        }
    }

    private void activatePremium() {
        StoredProfile playerProfile = core.getStorage().loadProfile(targetPlayer);
        if (playerProfile == null) {
            // null only on SQL exception — abort instead of NPE, give the invoker feedback
            core.getPlugin().getLog().warn("Cannot toggle premium state for {}: database query failed",
                    targetPlayer);
            sendMessage("database-error");
            return;
        }
        if (playerProfile.isOnlinemodePreferred()) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("{} is already premium; skipping toggle", targetPlayer);
            }
            boolean isSelf = senderName.equalsIgnoreCase(targetPlayer);
            sendMessage(isSelf ? "already-exists" : "already-exists-other");
            return;
        }

        playerProfile.setOnlinemodePreferred(true);

        // Resolve and store the Mojang UUID so the profile carries the
        // correct premium UUID — without this, on reconnect the proxy may
        // assign an offline UUID even though online mode is enabled.
        try {
            Optional<Profile> mojangProfile = core.getResolver().findProfile(targetPlayer);
            if (mojangProfile.isPresent()) {
                playerProfile.setId(mojangProfile.get().getId());
            }
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Mojang UUID resolution for {}: {}", targetPlayer,
                        playerProfile.getId());
            }
        } catch (Exception e) {
            core.getPlugin().getLog().warn(
                "Failed to resolve Mojang UUID for {} during premium toggle", targetPlayer, e);
        }

        core.getStorage().save(playerProfile);
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Marked {} as premium in proxy database", targetPlayer);
        }
        PremiumToggleReason reason = (!isPlayerSender || !senderName.equalsIgnoreCase(playerProfile.getName()))
            ? PremiumToggleReason.COMMAND_OTHER : PremiumToggleReason.COMMAND_SELF;
        core.getPlugin().getProxy().getEventManager().fire(
            new VelocityFastLoginPremiumToggleEvent(playerProfile, reason));
        boolean isSelf = senderName.equalsIgnoreCase(targetPlayer);
        sendMessage(isSelf ? "add-premium" : "add-premium-other");

        // Kick the target player so they reconnect with the updated profile
        // and the proxy assigns their premium UUID.
        if (core.getConfig().getBoolean("kick-toggle")) {
            Optional<Player> target = core.getPlugin().getProxy().getPlayer(targetPlayer);
            if (target.isPresent()) {
                if (core.isDebug()) {
                    core.getPlugin().getLog().info("Kicking {} to apply premium profile (kick-toggle)",
                            targetPlayer);
                }
                TextComponent msg = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(core.getMessage("add-premium"));
                target.get().disconnect(msg);
            }
        }
    }

    private void sendMessage(String localeId) {
        String message = core.getMessage(localeId);
        if (isPlayerSender) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
        } else {
            ConsoleCommandSource console = core.getPlugin().getProxy().getConsoleCommandSource();
            console.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
            // route the result back to the backend console that issued the
            // relayed command (over the carrier player's server connection)
            sendFeedbackToBackend(localeId);
        }
    }

    /**
     * Sends the toggle result back to the backend over the carrier player's
     * server connection so the backend console (where the relayed command was
     * typed) sees the outcome without having to check the proxy log.  The
     * payload carries a locale key — the backend renders it with its own
     * language file — plus this proxy's UUID, which the backend validates
     * against allowed-proxies.txt.
     *
     * @param localeId the locale key of the result message
     */
    private void sendFeedbackToBackend(String localeId) {
        Optional<ServerConnection> server = sender.getCurrentServer();
        if (!server.isPresent()) {
            // carrier has no backend connection (yet/anymore) — result stays
            // visible on this proxy's console only
            return;
        }
        try {
            core.getPlugin().sendPluginMessage(server.get(),
                    new ToggleFeedbackMessage(targetPlayer, localeId,
                            core.getPlugin().getProxyId()));
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Sent relay feedback to backend for {}: {}",
                        targetPlayer, localeId);
            }
        } catch (Exception e) {
            core.getPlugin().getLog().warn("Failed to send relay feedback for {}: {}",
                    targetPlayer, e.getMessage());
        }
    }
}
