/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.CrackedNoticeWire;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;

/** Selects Fabric rendering or the existing proxy chat, never both for one confirmation. */
public final class CrackedNoticeDelivery {
    private final FastLoginVelocity plugin;
    private final MinecraftChannelIdentifier channel;
    private final ConcurrentMap<ServerConnection, CrackedNoticeWire> capable = CacheBuilder.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5)).<ServerConnection, CrackedNoticeWire>build()
            .asMap();

    public CrackedNoticeDelivery(FastLoginVelocity plugin) {
        this.plugin = plugin;
        channel = MinecraftChannelIdentifier.create(plugin.getName(), CrackedNoticeWire.CHANNEL);
    }

    public void deliver(Player player, RegisteredServer target) {
        if (!plugin.getCore().hasSameNameCrackedNotice(player.getUsername())) {
            return;
        }
        ServerConnection connection = player.getCurrentServer().orElse(null);
        if (!player.isActive() || connection == null || connection.getServer() != target) {
            return; // Do not spend the notice on a disconnected or switched connection.
        }
        CrackedNoticeWire capability = capable.get(connection);
        if (capability != null && (!capability.name().equals(player.getUsername())
                || !capability.playerId().equals(player.getUniqueId()))) {
            capability = null;
        }
        finish(player, connection, capability);
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.equals(event.getIdentifier()) || !event.getResult().isAllowed()) {
            return;
        }
        // Never forward internal capability declarations to a client.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection) || !(event.getTarget() instanceof Player)) {
            return;
        }
        try {
            CrackedNoticeWire notice = CrackedNoticeWire.decode(event.getData());
            ServerConnection source = (ServerConnection) event.getSource();
            Player player = (Player) event.getTarget();
            if (notice.kind() != CrackedNoticeWire.Kind.CAPABLE || source.getPlayer() != player
                    || !player.isActive() || !notice.name().equals(player.getUsername())
                    || !notice.playerId().equals(player.getUniqueId())) {
                return;
            }
            if (plugin.getCore().getConfig().getBoolean("verify-backend-messages")
                    && !PluginMessageListener.accepts(notice.sourceProxyIds(), plugin.getProxyId())) {
                plugin.getLog().warn("Ignoring unauthenticated cracked-notice backend capability");
                return;
            }
            // Configuration may precede getCurrentServer(). Do not consume anything here:
            // the normal offline join task later binds this exact ServerConnection and chooses once.
            capable.putIfAbsent(source, notice);
        } catch (IOException malformed) {
            plugin.getLog().warn("Ignoring malformed cracked-notice backend capability");
        }
    }

    private void finish(Player player, ServerConnection connection, CrackedNoticeWire capability) {
        if (!player.isActive() || player.getCurrentServer().orElse(null) != connection) {
            return;
        }
        // Re-read at delivery: a premium toggle/verification may have cleared the mark.
        try {
            plugin.getCore().getStorage().withNameLock(player.getUsername(), () -> {
                StoredProfile current = plugin.getCore().getStorage().loadProfile(player.getUsername());
                if (current == null) {
                    return; // Read failure must not consume notification work.
                }
                if (!plugin.getCore().consumeSameNameCrackedNotice(player.getUsername())
                        || !current.isSameNameCracked()) {
                    return;
                }
                if (capability == null || !connection.sendPluginMessage(channel,
                        capability.withKind(CrackedNoticeWire.Kind.DELIVER).encode())) {
                    plugin.getCore().sendLocaleMessage("same-name-cracked-recorded", player);
                }
            });
        } catch (RuntimeException failed) {
            // At-most-once, like the original consume-before-chat path; do not retry an uncertain send.
            plugin.getLog().warn("Could not deliver the first same-name cracked notice for {}", player.getUsername(),
                    failed);
        }
    }

    public void disconnected(Player player) {
        capable.keySet().removeIf(connection -> connection.getPlayer() == player);
    }
}
