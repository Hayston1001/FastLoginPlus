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
package com.github.games647.fastlogin.bungee.task;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.event.BungeeFastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Optional;

public class AsyncToggleMessage implements Runnable {

    private final FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core;
    private final ProxiedPlayer sender;
    private final String senderName;
    private final String targetPlayer;
    private final boolean toPremium;
    private final boolean isPlayerSender;

    public AsyncToggleMessage(FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core,
             ProxiedPlayer sender, String playerName, boolean toPremium, boolean playerSender) {
        this.core = core;
        this.sender = sender;
        this.senderName = sender.getName();
        this.targetPlayer = playerName;
        this.toPremium = toPremium;
        this.isPlayerSender = playerSender;
    }

    @Override
    public void run() {
        if (toPremium) {
            activatePremium();
        } else {
            turnOffPremium();
        }
    }

    private void turnOffPremium() {
        StoredProfile playerProfile = core.getStorage().loadProfile(targetPlayer);
        //existing player is already cracked
        if (playerProfile.isExistingPlayer() && !playerProfile.isOnlinemodePreferred()) {
            boolean isSelf = senderName.equalsIgnoreCase(targetPlayer);
            sendMessage(isSelf ? "not-premium" : "not-premium-other");
            // Still kick if configured
            if (core.getConfig().getBoolean("kick-toggle")) {
                ProxiedPlayer target = core.getPlugin().getProxy().getPlayer(targetPlayer);
                if (target != null) {
                    target.disconnect(TextComponent.fromLegacyText(core.getMessage("remove-premium")));
                }
            }
            return;
        }

        playerProfile.setOnlinemodePreferred(false);
        playerProfile.setId(null);
        core.getStorage().save(playerProfile);
        PremiumToggleReason reason = (!isPlayerSender || !senderName.equalsIgnoreCase(playerProfile.getName()))
            ? PremiumToggleReason.COMMAND_OTHER : PremiumToggleReason.COMMAND_SELF;
        core.getPlugin().getProxy().getPluginManager().callEvent(
                new BungeeFastLoginPremiumToggleEvent(playerProfile, reason));

        boolean isSelf = senderName.equalsIgnoreCase(targetPlayer);
        sendMessage(isSelf ? "remove-premium" : "remove-premium-other");

        // Kick the target player so they reconnect with the updated profile
        if (core.getConfig().getBoolean("kick-toggle")) {
            ProxiedPlayer target = core.getPlugin().getProxy().getPlayer(targetPlayer);
            if (target != null) {
                target.disconnect(TextComponent.fromLegacyText(core.getMessage("remove-premium")));
            }
        }
    }

    private void activatePremium() {
        StoredProfile playerProfile = core.getStorage().loadProfile(targetPlayer);
        if (playerProfile.isOnlinemodePreferred()) {
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
        } catch (Exception e) {
            core.getPlugin().getLog().warn(
                "Failed to resolve Mojang UUID for {} during premium toggle", targetPlayer, e);
        }

        core.getStorage().save(playerProfile);
        PremiumToggleReason reason = (!isPlayerSender || !senderName.equalsIgnoreCase(playerProfile.getName()))
            ? PremiumToggleReason.COMMAND_OTHER : PremiumToggleReason.COMMAND_SELF;
        core.getPlugin().getProxy().getPluginManager().callEvent(
                new BungeeFastLoginPremiumToggleEvent(playerProfile, reason));
        boolean isSelf = senderName.equalsIgnoreCase(targetPlayer);
        sendMessage(isSelf ? "add-premium" : "add-premium-other");

        // Kick the target player so they reconnect with the updated profile
        // and the proxy assigns their premium UUID.
        if (core.getConfig().getBoolean("kick-toggle")) {
            ProxiedPlayer target = core.getPlugin().getProxy().getPlayer(targetPlayer);
            if (target != null) {
                target.disconnect(TextComponent.fromLegacyText(core.getMessage("add-premium")));
            }
        }
    }

    private void sendMessage(String localeId) {
        String message = core.getMessage(localeId);
        if (isPlayerSender) {
            sender.sendMessage(TextComponent.fromLegacyText(message));
        } else {
            CommandSender console = ProxyServer.getInstance().getConsole();
            console.sendMessage(TextComponent.fromLegacyText(message));
        }
    }
}
