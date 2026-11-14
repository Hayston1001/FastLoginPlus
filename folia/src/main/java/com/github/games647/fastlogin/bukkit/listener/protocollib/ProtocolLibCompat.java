/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.netty.channel.NettyChannelInjector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/**
 * Central access to ProtocolLib's login-phase internals (Netty injector / channel) shared by the
 * listeners and tasks in this package. Keeping the version-sensitive reflection in one place makes
 * it easy to audit and adjust when ProtocolLib changes its internals.
 */
final class ProtocolLibCompat {

    private ProtocolLibCompat() {
    }

    /**
     * Resolves the ProtocolLib Netty injector of a login-phase (temporary) player.
     *
     * @param log    the plugin logger
     * @param debug  whether debug output is enabled (config {@code debug})
     * @param player the login-phase player
     * @return the injector, or null if the player is already gone or the injector cannot be
     *         resolved (reflection failure, ProtocolLib API drift)
     */
    static NettyChannelInjector getInjector(Logger log, boolean debug, Player player) {
        try {
            MethodAccessor accessor = Accessors.getMethodAccessorOrNull(
                    TemporaryPlayerFactory.class, "getInjectorFromPlayer", Player.class
            );
            if (accessor == null) {
                debugLog(log, debug, player,
                        "ProtocolLib API drift — TemporaryPlayerFactory.getInjectorFromPlayer is "
                                + "unavailable, treating connection as gone", null);
                return null;
            }

            return (NettyChannelInjector) accessor.invoke(null, player);
        } catch (Exception ex) {
            debugLog(log, debug, player, "Failed to resolve ProtocolLib injector", ex);
            return null;
        }
    }

    /**
     * Resolves the Netty channel of a login-phase (temporary) player.
     *
     * @param log    the plugin logger
     * @param debug  whether debug output is enabled (config {@code debug})
     * @param player the login-phase player
     * @return the channel, or null if it cannot be resolved (player gone or reflection failure)
     */
    static Channel getChannel(Logger log, boolean debug, Player player) {
        NettyChannelInjector injector = getInjector(log, debug, player);
        if (injector == null) {
            return null;
        }

        try {
            return FuzzyReflection.getFieldValue(injector, Channel.class, true);
        } catch (Exception ex) {
            debugLog(log, debug, player, "Failed to resolve Netty channel of ProtocolLib injector", ex);
            return null;
        }
    }

    /**
     * Checks whether the login connection of the player is still active. A missing injector or
     * channel is the normal "player already gone" case and is not logged; only unexpected
     * reflection failures are reported (in debug mode).
     *
     * @param log    the plugin logger
     * @param debug  whether debug output is enabled (config {@code debug})
     * @param player the login-phase player
     * @return true if the player's Netty channel is present and active
     */
    static boolean isConnectionActive(Logger log, boolean debug, Player player) {
        Channel channel = getChannel(log, debug, player);
        return channel != null && channel.isActive();
    }

    /**
     * Refuses a login-phase connection with a reason the client can display.
     *
     * <p>{@link Player#kickPlayer(String)} only reaches the client in the play
     * state: during login the server records the reason but the client is
     * dropped without it. The login-state DISCONNECT packet is the only way to
     * show the configured message; the kick afterwards closes the connection on
     * the server side.</p>
     *
     * @param log     the plugin logger
     * @param debug   whether debug output is enabled (config {@code debug})
     * @param player  the login-phase player
     * @param message the localized reason shown to the client
     * @return true when the disconnect packet was sent
     */
    static boolean refuseLogin(Logger log, boolean debug, Player player, String message) {
        if (!isConnectionActive(log, debug, player)) {
            // The player is already gone (client cancel, vanilla 'Took too long to
            // log in', ...) — resolving the injector would fail and the kick itself
            // is moot.
            return false;
        }

        PacketContainer kickPacket = new PacketContainer(PacketType.Login.Server.DISCONNECT);
        kickPacket.getChatComponents().write(0, WrappedChatComponent.fromText(message));
        boolean sent = false;
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, kickPacket);
            sent = true;
        } catch (Exception ex) {
            // Narrow race: the connection can drop between the isConnectionActive()
            // check and the send above.
            debugLog(log, debug, player, "Failed to send the login-phase disconnect", ex);
        }

        try {
            //tell the server that we want to close the connection
            player.kickPlayer("Disconnect");
        } catch (RuntimeException ex) {
            debugLog(log, debug, player, "Failed to close the refused login", ex);
        }
        return sent;
    }

    private static void debugLog(Logger log, boolean debug, Player player, String message, Exception ex) {
        if (debug) {
            log.info("{} — player: {}, error: {}", message, player, ex == null ? "n/a" : ex.toString());
        }
    }
}
