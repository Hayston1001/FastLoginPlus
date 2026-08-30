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
package com.github.games647.fastlogin.bungee.listener;

import java.util.Arrays;
import java.util.UUID;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.event.BungeeFastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.bungee.task.AsyncToggleMessage;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.DeletePremiumMessage;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.message.ProxyAuthenticatedMessage;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.message.ToggleFeedbackMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PluginMessageListener implements Listener {

    private final FastLoginBungee plugin;

    private final String successChannel;
    private final String changeChannel;
    private final String deleteChannel;

    public PluginMessageListener(FastLoginBungee plugin) {
        this.plugin = plugin;

        this.successChannel = new NamespaceKey(plugin.getName(), SuccessMessage.SUCCESS_CHANNEL).getCombinedName();
        this.changeChannel = new NamespaceKey(plugin.getName(), ChangePremiumMessage.CHANGE_CHANNEL).getCombinedName();
        this.deleteChannel = new NamespaceKey(plugin.getName(), DeletePremiumMessage.DELETE_CHANNEL).getCombinedName();
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent pluginMessageEvent) {
        String channel = pluginMessageEvent.getTag();
        if (pluginMessageEvent.isCancelled() || !channel.startsWith(plugin.getName().toLowerCase())) {
            return;
        }

        //the client shouldn't be able to read the messages in order to know something about server internal states
        //moreover the client shouldn't be able to fake a running premium check by sending the result message
        pluginMessageEvent.setCancelled(true);

        if (!(pluginMessageEvent.getSender() instanceof Server)) {
            //check if the message is sent from the server
            return;
        }

        //so that we can safely process this in the background
        byte[] data = Arrays.copyOf(pluginMessageEvent.getData(), pluginMessageEvent.getData().length);
        ProxiedPlayer forPlayer = (ProxiedPlayer) pluginMessageEvent.getReceiver();

        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Received proxy plugin message from server on channel {} for {} size={}",
                    channel, forPlayer.getName(), data.length);
        }

        plugin.getScheduler().runAsync(() -> readMessage(forPlayer, channel, data));
    }

    private void readMessage(ProxiedPlayer forPlayer, String channel, byte[] data) {
        FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core = plugin.getCore();

        ByteArrayDataInput dataInput = ByteStreams.newDataInput(data);
        if (successChannel.equals(channel)) {
            SuccessMessage successMessage = new SuccessMessage();
            successMessage.readFrom(dataInput);
            if (rejectUnauthenticated(channel, successMessage)) {
                return;
            }

            onSuccessMessage(forPlayer);
        } else if (changeChannel.equals(channel)) {
            ChangePremiumMessage changeMessage = new ChangePremiumMessage();
            changeMessage.readFrom(dataInput);
            if (rejectUnauthenticated(channel, changeMessage)) {
                return;
            }

            String playerName = changeMessage.getPlayerName();
            boolean isSourceInvoker = changeMessage.isSourceInvoker();
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("ChangePremiumMessage: target={} enable={} sourceInvoker={} carrier={}",
                        playerName, changeMessage.shouldEnable(), isSourceInvoker, forPlayer.getName());
            }
            if (changeMessage.shouldEnable()) {
                boolean premiumWarning =
                        plugin.getCore().getConfig().getBoolean("premium-warning");
                // atomic check-and-add (0.5.0/F025): add() returns false when the
                // UUID is already pending, so two concurrent toggles for the same
                // player cannot both pass this gate and double-prompt
                if (isSourceInvoker && playerName.equals(forPlayer.getName()) && premiumWarning
                        && core.getPendingConfirms().add(forPlayer.getUniqueId())) {
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info("Premium-warning gate hit for {}: showing confirmation prompt, "
                                + "toggle deferred until the command is issued again", playerName);
                    }
                    String message = core.getMessage("premium-warning");
                    forPlayer.sendMessage(TextComponent.fromLegacyText(message));
                    return;
                }

                core.getPendingConfirms().remove(forPlayer.getUniqueId());
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Dispatching premium toggle task for {} (enable=true)", playerName);
                }
                Runnable task = new AsyncToggleMessage(core, forPlayer, playerName, true, isSourceInvoker);
                plugin.getScheduler().runAsync(task);
            } else {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Dispatching premium toggle task for {} (enable=false)", playerName);
                }
                Runnable task = new AsyncToggleMessage(core, forPlayer, playerName, false, isSourceInvoker);
                plugin.getScheduler().runAsync(task);
            }
        } else if (deleteChannel.equals(channel)) {
            DeletePremiumMessage deleteMessage = new DeletePremiumMessage();
            deleteMessage.readFrom(dataInput);
            if (rejectUnauthenticated(channel, deleteMessage)) {
                return;
            }

            String playerName = deleteMessage.getPlayerName();
            boolean isSourceInvoker = deleteMessage.isSourceInvoker();
            plugin.getScheduler().runAsync(() -> {
                StoredProfile profile = core.getStorage().loadProfile(playerName);
                if (profile == null) {
                    // null only on SQL exception (connection down / lock timeout) — do not
                    // report 'not found'; the database is the thing that failed
                    sendDeleteFeedback(forPlayer, isSourceInvoker, "database-error");
                    return;
                }
                if (!profile.isExistingPlayer()) {
                    sendDeleteFeedback(forPlayer, isSourceInvoker, "delete-not-found");
                    return;
                }
                if (profile.isOnlinemodePreferred()) {
                    sendDeleteFeedback(forPlayer, isSourceInvoker, "delete-premium-denied");
                    return;
                }
                if (core.getStorage().deleteProfile(playerName)) {
                    plugin.getProxy().getPluginManager().callEvent(
                            new BungeeFastLoginPremiumToggleEvent(profile,
                                    PremiumToggleReason.COMMAND_OTHER));
                    sendDeleteFeedback(forPlayer, isSourceInvoker, "delete-success");
                } else {
                    // affected rows == 0: distinguish real failure from concurrent removal
                    StoredProfile after = core.getStorage().loadProfile(playerName);
                    if (after == null || !after.isExistingPlayer()) {
                        sendDeleteFeedback(forPlayer, isSourceInvoker, "delete-not-found");
                    } else {
                        sendDeleteFeedback(forPlayer, isSourceInvoker, "delete-fail");
                    }
                }
            });
        }
    }

    private void sendDeleteFeedback(ProxiedPlayer carrier, boolean isSourceInvoker, String localeId) {
        FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core = plugin.getCore();
        String message = core.getMessage(localeId);
        if (isSourceInvoker) {
            carrier.sendMessage(TextComponent.fromLegacyText(message));
        } else {
            ProxyServer.getInstance().getConsole().sendMessage(TextComponent.fromLegacyText(message));
            // mirror the delete result to the backend console that issued the
            // relayed command (over the carrier player's server connection)
            Server server = carrier.getServer();
            if (server != null) {
                try {
                    UUID proxyId = UUID.fromString(
                            ProxyServer.getInstance().getConfig().getUuid());
                    plugin.sendPluginMessage(server,
                            new ToggleFeedbackMessage(carrier.getName(), localeId, proxyId));
                } catch (Exception ex) {
                    plugin.getLog().warn("Failed to send delete feedback to backend: {}",
                            ex.getMessage());
                }
            }
        }
    }

    private void onSuccessMessage(ProxiedPlayer forPlayer) {
        boolean shouldPersist = forPlayer.getPendingConnection().isOnlineMode();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (!shouldPersist && floodgateService != null) {
            // always save floodgate players to lock this username
            shouldPersist = floodgateService.isBedrockPlayer(forPlayer.getUniqueId());
        }

        if (shouldPersist) {
            //bukkit module successfully received and force logged in the user
            //update only on success to prevent corrupt data
            BungeeLoginSession loginSession = plugin.getSession().get(forPlayer.getPendingConnection());
            // 0.5.0/F055: the player may have disconnected between the message
            // arriving and this async task running — nothing to persist then
            if (loginSession == null) {
                plugin.getLog().info("No active session for {} on success message"
                        + " — the player probably disconnected", forPlayer.getName());
                return;
            }
            StoredProfile playerProfile = loginSession.getProfile();
            loginSession.setRegistered(true);

            if (!loginSession.isAlreadySaved()) {
                playerProfile.setOnlinemodePreferred(true);
                plugin.getCore().getStorage().save(playerProfile);
                loginSession.setAlreadySaved(true);
            }
        }
    }

    /**
     * 0.5.0/F054: backend -&gt; proxy messages echo the sending backend's proxy allowlist;
     * the message is only trusted when this proxy's own ID is part of that set.
     *
     * @param channel the plugin message channel (for the warning log)
     * @param message the received message carrying the echoed allowlist
     * @return true when the message must be dropped (strict mode and unauthenticated)
     */
    private boolean rejectUnauthenticated(String channel, ProxyAuthenticatedMessage message) {
        if (!plugin.getCore().getConfig().getBoolean("verify-backend-messages")) {
            // rolling-upgrade escape hatch; see config-proxy.yml
            return false;
        }

        if (accepts(message.getSourceProxyId(), ownProxyId())) {
            return false;
        }

        plugin.getLog().warn("Unauthenticated backend message on {} — echoed proxy id set '{}'"
                + " does not contain this proxy's ID; dropping it", channel, message.getSourceProxyId());
        return true;
    }

    /**
     * Pure authentication decision for backend -&gt; proxy messages (0.5.0/F054).
     *
     * @param echoedProxyIds comma-joined proxy IDs echoed by the sending backend
     * @param ownProxyId this proxy's own ID
     * @return true only when {@code ownProxyId} is a member of the echoed set;
     *         false for empty/legacy payloads, unparsable IDs or a null own ID (fail-closed)
     */
    static boolean accepts(String echoedProxyIds, UUID ownProxyId) {
        if (ownProxyId == null || echoedProxyIds == null || echoedProxyIds.isEmpty()) {
            return false;
        }

        for (String candidate : echoedProxyIds.split(",")) {
            try {
                if (ownProxyId.equals(UUID.fromString(candidate.trim()))) {
                    return true;
                }
            } catch (IllegalArgumentException malformedEntry) {
                // skip malformed entries but keep checking the rest of the set
            }
        }

        return false;
    }

    private UUID ownProxyId() {
        try {
            return UUID.fromString(ProxyServer.getInstance().getConfig().getUuid());
        } catch (RuntimeException notAUuid) {
            return null;
        }
    }
}
