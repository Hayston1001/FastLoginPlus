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

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.task.AsyncPremiumCheck;
import com.github.games647.fastlogin.bungee.task.FloodgateAuthTask;
import com.github.games647.fastlogin.bungee.task.ForceLoginTask;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;

import com.github.games647.fastlogin.bungee.event.BungeeFastLoginAntiBotEvent;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.shared.ForwardingAttributes;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.google.common.base.Throwables;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Enables online mode logins for specified users and sends plugin message to the Bukkit version of this plugin in
 * order to clear that the connection is online mode.
 */
public class ConnectListener implements Listener {

    private static final String UUID_FIELD_NAME = "uniqueId";
    protected static final MethodHandle UNIQUE_ID_SETTER;

    private static final String REWRITE_ID_NAME = "rewriteId";
    protected static final MethodHandle REWRITE_ID_SETTER;

    static {
        MethodHandle uniqueIdHandle = null;
        MethodHandle rewriterHandle = null;
        try {
            Lookup lookup = MethodHandles.lookup();

            // test for implementation class availability
            Class.forName("net.md_5.bungee.connection.InitialHandler");
            uniqueIdHandle = getHandlerSetter(lookup, UUID_FIELD_NAME);
            try {
                rewriterHandle = getHandlerSetter(lookup, REWRITE_ID_NAME);
            } catch (NoSuchFieldException noSuchFieldEx) {
                Logger logger = LoggerFactory.getLogger(ConnectListener.class);
                logger.error(
                        "Rewrite field not found. Setting only legacy BungeeCord field"
                );
            }
        } catch (ReflectiveOperationException reflectiveOperationException) {
            Logger logger = LoggerFactory.getLogger(ConnectListener.class);
            logger.error(
                    "Cannot find Bungee UUID field implementation; Disabling premium UUID and skin won't work.",
                    reflectiveOperationException
            );
        }

        UNIQUE_ID_SETTER = uniqueIdHandle;
        REWRITE_ID_SETTER = rewriterHandle;
    }

    private static MethodHandle getHandlerSetter(Lookup lookup, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field uuidField = InitialHandler.class.getDeclaredField(fieldName);
        uuidField.setAccessible(true);
        return lookup.unreflectSetter(uuidField);
    }

    private final FastLoginBungee plugin;
    private final AntiBotService antiBotService;

    public ConnectListener(FastLoginBungee plugin, AntiBotService antiBotService) {
        this.plugin = plugin;
        this.antiBotService = antiBotService;
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent preLoginEvent) {
        PendingConnection connection = preLoginEvent.getConnection();
        if (preLoginEvent.isCancelled()) {
            return;
        }

        InetSocketAddress address = preLoginEvent.getConnection().getAddress();
        String username = connection.getName();

        plugin.getLog().info("Incoming login request for {} from {}", username, connection.getSocketAddress());

        Action action = antiBotService.onIncomingConnection(address, username);
        if (action != Action.Continue) {
            BungeeFastLoginAntiBotEvent antiBotEvent = new BungeeFastLoginAntiBotEvent(address, username, action);
            plugin.getProxy().getPluginManager().callEvent(antiBotEvent);
            if (antiBotEvent.isCancelled()) {
                action = Action.Continue;
            }
        }

        switch (action) {
            case Ignore:
                // just ignore
                return;
            case Block:
                String message = plugin.getCore().getMessage("kick-antibot");
                preLoginEvent.setCancelReason(TextComponent.fromLegacyText(message));
                preLoginEvent.setCancelled(true);
                break;
            case Continue:
            default:
                preLoginEvent.registerIntent(plugin);
                Runnable asyncPremiumCheck = new AsyncPremiumCheck(plugin, preLoginEvent, connection, username);
                plugin.getScheduler().runAsync(asyncPremiumCheck);
                break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(LoginEvent loginEvent) {
        if (loginEvent.isCancelled()) {
            return;
        }

        //use the login event instead of the post login event in order to send the login success packet to the client
        //with the offline uuid this makes it possible to set the skin then
        PendingConnection connection = loginEvent.getConnection();
        if (connection.isOnlineMode()) {
            LoginSession session = plugin.getSession().get(connection);
            // 0.5.0/F053: no FLP session exists for unknown players when the
            // proxy itself runs in online mode (or a third-party plugin enabled
            // it) — skip instead of NPE-ing on the event thread
            if (session == null) {
                plugin.getLog().warn("No active login session for online-mode player {}"
                        + " — skipping FastLogin session update", connection.getName());
                return;
            }

            UUID verifiedUUID = connection.getUniqueId();
            String verifiedUsername = connection.getName();
            session.setUuid(verifiedUUID);
            session.setVerifiedUsername(verifiedUsername);

            StoredProfile playerProfile = session.getProfile();
            playerProfile.setId(verifiedUUID);

            if (connection instanceof InitialHandler) {
                InitialHandler initialHandler = (InitialHandler) connection;

                // Clear the forwarded properties first: the attestation below appends to the very
                // same array, so it has to run after this. The operation used to be a typed call
                // on LoginResult#setProperties with a compile-time Property[] — that reference no
                // longer links on proxy builds released after BungeeCord #3855 (2025-07), which
                // took the whole listener down at construction time (0.7.0/F17).
                if (!(boolean) plugin.getCore().getConfig().get("forwardSkin")) {
                    clearForwardedProperties(initialHandler, verifiedUsername);
                }

                if (!(boolean) plugin.getCore().getConfig().get("premiumUuid")) {
                    // BungeeCord will do this automatically so override it on disabled option.
                    // Gated on the method handles: rewriting the connection's UUID needs private
                    // fields, while the attestation below does not — hence the separate branch.
                    if (UNIQUE_ID_SETTER != null) {
                        setOfflineId(initialHandler, verifiedUsername);
                    }

                    // 0.7.0/F17: hand the verified Mojang UUID to the backend over the legacy
                    // handshake, so it can pre-create the AuthMe record before the preJoin dialog
                    // is shown — the same guarantee the Velocity side gets from F13.
                    attachPremiumAttestation(initialHandler, verifiedUsername, verifiedUUID);
                }
            } else {
                plugin.getLog().warn("Unexpected connection type {} — cannot update the login"
                        + " profile for {}", connection.getClass().getName(), verifiedUsername);
            }
        }
    }

    /**
     * Replaces the login profile's properties with an empty array, dropping the skin the client
     * sent along (the {@code forwardSkin: false} behaviour).
     *
     * @param connection the login connection being established
     * @param username   the player name, for log messages
     */
    private void clearForwardedProperties(InitialHandler connection, String username) {
        LoginProfileProperties.clear(connection.getLoginProfile(), failure -> {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Could not clear the login profile properties for {}: {}",
                        username, failure.toString());
            }
        });
    }

    /**
     * Hands the verified Mojang UUID to the backend as a login profile property.
     *
     * <p>BungeeCord serialises those properties into the handshake it sends to the backend when
     * IP forwarding is enabled, and the backend reads the value back in the configuration phase
     * — before AuthMe can show its preJoin dialog (0.7.0/F13 on the Velocity side, F17 here).</p>
     *
     * <p>The name is not the one Velocity uses: Paper drops legacy-forwarded properties whose
     * name is not {@code \w{0,16}}, and that spelling contains hyphens
     * ({@link ForwardingAttributes#PREMIUM_UUID_LEGACY}).</p>
     *
     * @param connection  the login connection being established
     * @param username    the player name, for log messages
     * @param premiumUuid the Mojang UUID verified for this connection, before the offline rewrite
     */
    private void attachPremiumAttestation(InitialHandler connection, String username,
                                          UUID premiumUuid) {
        boolean attached = LoginProfileProperties.attach(connection.getLoginProfile(),
                ForwardingAttributes.PREMIUM_UUID_LEGACY, premiumUuid.toString(),
                failure -> plugin.getLog().warn("Could not attach the verified premium UUID for {}"
                        + " to the login profile — AuthMe's first-login dialog is not skipped"
                        + " (0.7.0/F17): {}", username, failure.toString()));
        if (attached) {
            plugin.getLog().info("Attaching verified premium UUID {} to the forwarded profile",
                    premiumUuid);
        }
    }

    protected void setOfflineId(InitialHandler connection, String username) {
        try {
            UUID oldPremiumId = connection.getUniqueId();
            UUID offlineUUID = UUIDAdapter.generateOfflineId(username);

            // BungeeCord only allows setting the UUID in PreLogin events and before requesting online mode
            // However if online mode is requested, it will override previous values
            // So we have to do it with reflection
            UNIQUE_ID_SETTER.invokeExact(connection, offlineUUID);

            // if available set rewrite id to forward the UUID for newer BungeeCord versions since
            // https://github.com/SpigotMC/BungeeCord/commit/1be25b6c74ec2be4b15adf8ca53a0497f01e2afe
            if (REWRITE_ID_SETTER != null) {
                REWRITE_ID_SETTER.invokeExact(connection, offlineUUID);
            }

            String format = "Overridden UUID from {} to {} (based of {}) on {}";
            plugin.getLog().info(format, oldPremiumId, offlineUUID, username, connection);
        } catch (Exception ex) {
            plugin.getLog().error("Failed to set offline uuid of {}", username, ex);
        } catch (Throwable throwable) {
            // throw remaining exceptions like out of memory that we shouldn't handle ourselves
            Throwables.throwIfUnchecked(throwable);
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent serverConnectedEvent) {
        ProxiedPlayer player = serverConnectedEvent.getPlayer();
        Server server = serverConnectedEvent.getServer();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (floodgateService != null) {
            FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(player.getUniqueId());
            if (floodgatePlayer != null) {
                Runnable floodgateAuthTask = new FloodgateAuthTask(plugin.getCore(), player, floodgatePlayer, server);
                plugin.getScheduler().runAsync(floodgateAuthTask);
                return;
            }
        }

        BungeeLoginSession session = plugin.getSession().get(player.getPendingConnection());
        if (session == null) {
            return;
        }

        // delay sending force command, because Paper will process the login event asynchronously
        // In this case it means that the force command (plugin message) is already received and processed while
        // player is still in the login phase and reported to be offline.
        Runnable loginTask = new ForceLoginTask(plugin.getCore(), player, server, session);
        plugin.getScheduler().runAsync(loginTask);
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent disconnectEvent) {
        ProxiedPlayer player = disconnectEvent.getPlayer();
        plugin.getSession().remove(player.getPendingConnection());
        plugin.getCore().getPendingConfirms().remove(player.getUniqueId());
    }
}
