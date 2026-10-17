/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.CrackedNoticeClaim;
import com.github.games647.fastlogin.protocol.CrackedNoticeWire;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.google.common.cache.CacheBuilder;
import com.google.common.base.Ticker;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/** Tracks notification acknowledgements and server rendering markers, never authentication. */
public final class CrackedNoticeDelivery {
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);
    private final FastLoginVelocity plugin;
    private final MinecraftChannelIdentifier channel;
    private final ConcurrentMap<RegisteredServer, Boolean> renderingServers;
    private final ConcurrentMap<InetSocketAddress, Attempt> attempts = CacheBuilder.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5)).<InetSocketAddress, Attempt>build().asMap();

    private static final class Attempt {
        private final String name;
        private final UUID playerId;
        private final CrackedNoticeClaim claim;
        private final InboundConnection loginConnection;
        private Player owner;
        private ServerConnection connection;
        private boolean armed;
        private boolean settled;
        private boolean warnOnFallback;

        Attempt(String name, UUID playerId, CrackedNoticeClaim claim, InboundConnection loginConnection) {
            this.name = name;
            this.playerId = playerId;
            this.claim = claim;
            this.loginConnection = loginConnection;
        }

        boolean matches(String expectedName, UUID expectedId) {
            return name.equals(expectedName) && playerId.equals(expectedId);
        }
    }

    public CrackedNoticeDelivery(FastLoginVelocity plugin) {
        this(plugin, Ticker.systemTicker());
    }

    CrackedNoticeDelivery(FastLoginVelocity plugin, Ticker ticker) {
        this.plugin = plugin;
        channel = MinecraftChannelIdentifier.create(plugin.getName(), CrackedNoticeWire.CHANNEL);
        renderingServers = CacheBuilder.newBuilder().maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(12)).ticker(ticker).<RegisteredServer, Boolean>build().asMap();
    }

    /**
     * Reserves this login's nonce without consuming the name-keyed notification debt.
     *
     * @param connection this login's original inbound connection
     * @param name forwarded player name
     * @param playerId forwarded player UUID
     * @return the optional notice when the name still owes one
     */
    public CrackedNoticeClaim prepare(InboundConnection connection, String name, UUID playerId) {
        if (!plugin.getCore().hasSameNameCrackedNotice(name)) {
            return null;
        }
        Attempt attempt = attempts.compute(connection.getRemoteAddress(), (key, old) ->
                old != null && old.loginConnection == connection && old.matches(name, playerId)
                        ? old : new Attempt(name, playerId,
                        new CrackedNoticeClaim(true, name, playerId, UUID.randomUUID()), connection));
        return attempt.claim;
    }

    public void deliver(Player player, RegisteredServer target) {
        if (!plugin.getCore().hasSameNameCrackedNotice(player.getUsername())) {
            return;
        }
        ServerConnection connection = player.getCurrentServer().orElse(null);
        if (!player.isActive() || connection == null || connection.getServer() != target) {
            return;
        }
        Attempt attempt = attempts.computeIfAbsent(player.getRemoteAddress(), key ->
                new Attempt(player.getUsername(), player.getUniqueId(), null, null));
        synchronized (attempt) {
            if (attempt.settled || attempt.armed || !attempt.matches(player.getUsername(), player.getUniqueId())
                    || (attempt.owner != null && attempt.owner != player)
                    || (attempt.connection != null && attempt.connection != connection)) {
                return;
            }
            attempt.connection = connection;
            attempt.owner = player;
            attempt.armed = true;
            attempt.warnOnFallback = renderingServers.containsKey(target);
            // After a proxy restart the first Fabric connections may not have announced yet.
            // Use immediate, quiet proxy chat then; never make classic servers wait for an ACK.
            // The forwarded claim remains, so the backend may later render a second notice.
            // This accepted cold-path duplicate is preferable to losing the notice.
            if (!attempt.warnOnFallback || attempt.claim == null) {
                settle(player, attempt, false);
                return;
            }
            try {
                plugin.getScheduler().runAsyncDelayed(() -> settle(player, attempt, false), ACK_TIMEOUT);
            } catch (RuntimeException schedulingFailed) {
                settle(player, attempt, false);
            }
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.equals(event.getIdentifier()) || !event.getResult().isAllowed()) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection) || !(event.getTarget() instanceof Player)) {
            return;
        }
        try {
            CrackedNoticeWire ack = CrackedNoticeWire.decode(event.getData());
            ServerConnection source = (ServerConnection) event.getSource();
            Player player = (Player) event.getTarget();
            if (ack.kind() == CrackedNoticeWire.Kind.CAPABLE) {
                if (source.getPlayer() == player && player.isActive() && ack.name().equals(player.getUsername())
                        && ack.playerId().equals(player.getUniqueId())) {
                    if (plugin.getCore().getConfig().getBoolean("verify-backend-messages")
                            && !PluginMessageListener.accepts(ack.sourceProxyIds(), plugin.getProxyId())) {
                        plugin.getLog().warn("Ignoring unauthenticated cracked-notice backend capability");
                        return;
                    }
                    // A server-level rendering hint only: it grants no trust and settles no notice.
                    renderingServers.put(source.getServer(), Boolean.TRUE);
                }
                return;
            }
            Attempt attempt = attempts.get(player.getRemoteAddress());
            if (source.getPlayer() != player || !player.isActive() || attempt == null || attempt.claim == null
                    || !attempt.matches(player.getUsername(), player.getUniqueId()) || !attempt.claim.matches(ack)) {
                return;
            }
            if (plugin.getCore().getConfig().getBoolean("verify-backend-messages")
                    && !PluginMessageListener.accepts(ack.sourceProxyIds(), plugin.getProxyId())) {
                plugin.getLog().warn("Ignoring unauthenticated cracked-notice acknowledgement");
                return;
            }
            synchronized (attempt) {
                ServerConnection current = player.getCurrentServer().orElse(null);
                if (attempt.settled || (current != null && current != source)
                        || (attempt.owner != null && attempt.owner != player)
                        || (attempt.connection != null && attempt.connection != source)) {
                    return;
                }
                attempt.connection = source;
                attempt.owner = player;
            }
            // Storage must not block the proxy's message event thread.
            plugin.getScheduler().runAsync(() -> settle(player, attempt, true));
        } catch (IOException malformed) {
            plugin.getLog().warn("Ignoring malformed cracked-notice acknowledgement");
        } catch (RuntimeException unavailable) {
            plugin.getLog().warn("Could not process cracked-notice acknowledgement; notification debt is retained",
                    unavailable);
        }
    }

    private void settle(Player player, Attempt attempt, boolean acknowledged) {
        synchronized (attempt) {
            ServerConnection current = player.getCurrentServer().orElse(null);
            if (attempt.settled || !player.isActive() || attempts.get(player.getRemoteAddress()) != attempt
                    || (!acknowledged && current == null) || (current != null && current != attempt.connection)) {
                return;
            }
            try {
                plugin.getCore().getStorage().withNameLock(attempt.name, () -> {
                    StoredProfile record = plugin.getCore().getStorage().loadProfile(attempt.name);
                    if (record == null) {
                        return; // Unknown storage state never spends notification debt.
                    }
                    ServerConnection latest = player.getCurrentServer().orElse(null);
                    if (!player.isActive() || attempts.get(player.getRemoteAddress()) != attempt
                            || (!acknowledged && latest == null)
                            || (latest != null && latest != attempt.connection)) {
                        return; // Storage may have blocked while this connection was replaced or disconnected.
                    }
                    if (!record.isSameNameCracked()) {
                        plugin.getCore().consumeSameNameCrackedNotice(attempt.name);
                        attempt.settled = true;
                        return;
                    }
                    if (!plugin.getCore().hasSameNameCrackedNotice(attempt.name)) {
                        attempt.settled = true;
                        return;
                    }
                    if (!acknowledged) {
                        // ACK loss looks like render failure. Prefer a duplicate to a lost notice.
                        attempt.settled = true;
                        plugin.getCore().sendLocaleMessage("same-name-cracked-recorded", player);
                        if (attempt.warnOnFallback) {
                            plugin.getLog().warn("Sending the first same-name cracked notice for {} through proxy chat "
                                    + "because the backend did not acknowledge rendering", attempt.name);
                        }
                    }
                    plugin.getCore().consumeSameNameCrackedNotice(attempt.name);
                    attempt.settled = true;
                });
            } catch (RuntimeException failed) {
                // Keep name-keyed debt for a reconnect if fallback chat or storage failed.
                plugin.getLog().warn("Could not settle the first same-name cracked notice for {}",
                        attempt.name, failed);
            }
        }
    }

    public void disconnected(Player player) {
        Attempt attempt = attempts.get(player.getRemoteAddress());
        if (attempt != null) {
            synchronized (attempt) {
                if (attempt.owner == player && attempts.remove(player.getRemoteAddress(), attempt)) {
                    attempt.settled = true;
                }
            }
        }
    }
}
