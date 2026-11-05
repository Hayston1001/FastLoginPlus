/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.CrackedNoticeClaim;
import com.github.games647.fastlogin.protocol.CrackedNoticeWire;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Tracks notification acknowledgements and server rendering markers, never authentication. */
public final class CrackedNoticeDelivery implements Listener {
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);
    private final FastLoginBungee plugin;
    private final String channel;
    private final Supplier<UUID> proxyIds;
    private final ConcurrentMap<String, Boolean> renderingServers;
    private final ConcurrentMap<InetSocketAddress, Attempt> attempts = CacheBuilder.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5)).<InetSocketAddress, Attempt>build().asMap();

    private static final class Attempt {
        private final String name;
        private final UUID playerId;
        private final CrackedNoticeClaim claim;
        private final PendingConnection loginConnection;
        private ProxiedPlayer owner;
        private Server connection;
        private boolean armed;
        private boolean settled;
        private boolean warnOnFallback;

        Attempt(String name, UUID playerId, CrackedNoticeClaim claim, PendingConnection loginConnection) {
            this.name = name;
            this.playerId = playerId;
            this.claim = claim;
            this.loginConnection = loginConnection;
        }

        boolean matches(String expectedName, UUID expectedId) {
            return name.equals(expectedName) && playerId.equals(expectedId);
        }
    }

    public CrackedNoticeDelivery(FastLoginBungee plugin) {
        this(plugin, Ticker.systemTicker());
    }

    CrackedNoticeDelivery(FastLoginBungee plugin, Ticker ticker) {
        this(plugin, ticker, CrackedNoticeDelivery::configuredProxyId);
    }

    CrackedNoticeDelivery(FastLoginBungee plugin, Ticker ticker, Supplier<UUID> proxyIds) {
        this.plugin = plugin;
        this.proxyIds = proxyIds;
        channel = NamespaceKey.getCombined(plugin.getName(), CrackedNoticeWire.CHANNEL);
        renderingServers = CacheBuilder.newBuilder().maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(12)).ticker(ticker).<String, Boolean>build().asMap();
    }

    /**
     * Reserves this login's nonce without consuming the name-keyed notification debt.
     *
     * @param connection this login's original inbound connection
     * @param name forwarded player name
     * @param playerId forwarded player UUID
     * @return the optional notice when the name still owes one
     */
    public CrackedNoticeClaim prepare(PendingConnection connection, String name, UUID playerId) {
        if (!plugin.getCore().hasSameNameCrackedNotice(name)) {
            return null;
        }
        Attempt attempt = attempts.compute(connection.getAddress(), (key, old) ->
                old != null && old.loginConnection == connection && old.matches(name, playerId)
                        ? old : new Attempt(name, playerId,
                        new CrackedNoticeClaim(true, name, playerId, UUID.randomUUID()), connection));
        return attempt.claim;
    }

    public void deliver(ProxiedPlayer player, Server target) {
        if (!plugin.getCore().hasSameNameCrackedNotice(player.getName())) {
            return;
        }
        Server connection = player.getServer();
        if (!player.isConnected() || connection == null || connection != target) {
            return;
        }
        Attempt attempt = attempts.computeIfAbsent(player.getAddress(), key ->
                new Attempt(player.getName(), player.getUniqueId(), null, null));
        synchronized (attempt) {
            if (attempt.settled || attempt.armed || !attempt.matches(player.getName(), player.getUniqueId())
                    || (attempt.owner != null && attempt.owner != player)
                    || (attempt.connection != null && attempt.connection != connection)) {
                return;
            }
            attempt.connection = connection;
            attempt.owner = player;
            attempt.armed = true;
            attempt.warnOnFallback = renderingServers.containsKey(target.getInfo().getName());
            // After a proxy restart the first backends may not have announced yet.
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

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.equals(event.getTag())) {
            return;
        }
        // BungeeCord cannot tell other plugins that it consumed the message, so
        // cancellation is both the "handled" marker and the delivery guard.
        event.setCancelled(true);
        if (!(event.getSender() instanceof Server) || !(event.getReceiver() instanceof ProxiedPlayer)) {
            return;
        }
        try {
            CrackedNoticeWire ack = CrackedNoticeWire.decode(event.getData());
            Server source = (Server) event.getSender();
            ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
            if (ack.kind() == CrackedNoticeWire.Kind.CAPABLE) {
                // BungeeCord builds the event from this player's own backend
                // connection, so the sender-to-player association is implicit.
                if (player.isConnected() && ack.name().equals(player.getName())
                        && ack.playerId().equals(player.getUniqueId())) {
                    if (plugin.getCore().getConfig().getBoolean("verify-backend-messages")
                            && !PluginMessageListener.accepts(ack.sourceProxyIds(), proxyIds.get())) {
                        plugin.getLog().warn("Ignoring unauthenticated cracked-notice backend capability");
                        return;
                    }
                    // A server-level rendering hint only: it grants no trust and settles no notice.
                    renderingServers.put(source.getInfo().getName(), Boolean.TRUE);
                }
                return;
            }
            Attempt attempt = attempts.get(player.getAddress());
            if (!player.isConnected() || attempt == null || attempt.claim == null
                    || !attempt.matches(player.getName(), player.getUniqueId()) || !attempt.claim.matches(ack)) {
                return;
            }
            if (plugin.getCore().getConfig().getBoolean("verify-backend-messages")
                    && !PluginMessageListener.accepts(ack.sourceProxyIds(), proxyIds.get())) {
                plugin.getLog().warn("Ignoring unauthenticated cracked-notice acknowledgement");
                return;
            }
            synchronized (attempt) {
                Server current = player.getServer();
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

    private void settle(ProxiedPlayer player, Attempt attempt, boolean acknowledged) {
        synchronized (attempt) {
            Server current = player.getServer();
            if (attempt.settled || !player.isConnected() || attempts.get(player.getAddress()) != attempt
                    || (!acknowledged && current == null) || (current != null && current != attempt.connection)) {
                return;
            }
            try {
                plugin.getCore().getStorage().withNameLock(attempt.name, () -> {
                    StoredProfile record = plugin.getCore().getStorage().loadProfile(attempt.name);
                    if (record == null) {
                        return; // Unknown storage state never spends notification debt.
                    }
                    Server latest = player.getServer();
                    if (!player.isConnected() || attempts.get(player.getAddress()) != attempt
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

    public void disconnected(ProxiedPlayer player) {
        Attempt attempt = attempts.get(player.getAddress());
        if (attempt != null) {
            synchronized (attempt) {
                if (attempt.owner == player && attempts.remove(player.getAddress(), attempt)) {
                    attempt.settled = true;
                }
            }
        }
    }

    private static UUID configuredProxyId() {
        try {
            return UUID.fromString(ProxyServer.getInstance().getConfig().getUuid());
        } catch (RuntimeException notAUuid) {
            return null;
        }
    }
}
