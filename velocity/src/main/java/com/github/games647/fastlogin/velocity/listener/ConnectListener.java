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
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;

import com.github.games647.fastlogin.velocity.event.VelocityFastLoginAntiBotEvent;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.task.AsyncPremiumCheck;
import com.github.games647.fastlogin.velocity.task.FloodgateAuthTask;
import com.github.games647.fastlogin.velocity.task.ForceLoginTask;
import com.google.common.cache.Cache;
import com.google.common.collect.ListMultimap;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.GameProfile.Property;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.atomic.AtomicBoolean;
public class ConnectListener {

    private static final String FLOODGATE_PLUGIN_NAME = "org.geysermc.floodgate.VelocityPlugin";

    private final FastLoginVelocity plugin;
    private final AntiBotService antiBotService;

    public ConnectListener(FastLoginVelocity plugin, AntiBotService antiBotService) {
        this.plugin = plugin;
        this.antiBotService = antiBotService;
    }

    @Subscribe
    public EventTask onPreLogin(PreLoginEvent preLoginEvent) {
        if (!preLoginEvent.getResult().isAllowed()) {
            return null;
        }

        InboundConnection connection = preLoginEvent.getConnection();
        String username = preLoginEvent.getUsername();
        InetSocketAddress address = connection.getRemoteAddress();
        plugin.getLog().info("Incoming login request for {} from {}", username, address);

        // FloodgateVelocity only sets the correct username in GetProfileRequestEvent, but we need it here too.
        if (plugin.getFloodgateService() != null) {
            String floodgateUsername = getFloodgateUsername(connection);
            if (floodgateUsername != null) {
                plugin.getLog().info("Found player's Floodgate: {}", floodgateUsername);
                username = floodgateUsername;
            }
        }

        // effectively-final snapshot for the lambda captures below (username
        // may have been rewritten by the Floodgate lookup above)
        final String checkedUsername = username;
        Action action = antiBotService.onIncomingConnection(address, checkedUsername);
        if (action != Action.Continue) {
            VelocityFastLoginAntiBotEvent antiBotEvent =
                    new VelocityFastLoginAntiBotEvent(address, checkedUsername, action);
            // Non-blocking (0.5.0/F034): pause the event, fire the anti-bot
            // event asynchronously and resume from the completion callback —
            // the Netty event loop must never block on a synchronous .get().
            // 0.5.0/R2: per-event guard so the continuation is resumed exactly
            // once even when the callback or the decision applying throws
            AtomicBoolean resumed = new AtomicBoolean(false);
            return EventTask.withContinuation(continuation -> {
                try {
                    plugin.getProxy().getEventManager().fire(antiBotEvent).whenComplete((unused, ex) -> {
                        if (ex != null) {
                            plugin.getLog().error("Error firing anti-bot event", ex);
                        }
                        Action effective = antiBotEvent.isCancelled() ? Action.Continue : action;
                        applyDecisionSafely(preLoginEvent, connection, checkedUsername, effective,
                                continuation, resumed);
                    });
                } catch (Exception fireEx) {
                    plugin.getLog().error("Error firing anti-bot event", fireEx);
                    resumeOnce(continuation, resumed);
                }
            });
        }

        // no anti-bot action — continue with the premium check
        // 0.5.0/F056: run it on the plugin scheduler instead of the shared
        // async event executor, so blocking Mojang lookups (with retry sleeps)
        // cannot starve the event executor for all other handlers
        // 0.5.0/R2: per-event guard so the continuation is resumed exactly once
        AtomicBoolean resumed = new AtomicBoolean(false);
        return EventTask.withContinuation(continuation ->
                applyDecisionSafely(preLoginEvent, connection, checkedUsername, action, continuation,
                        resumed));
    }

    /**
     * Apply the effective anti-bot decision to the pre-login event and resume
     * the paused event pipeline.
     *
     * @param preLoginEvent the paused pre-login event
     * @param connection    the inbound connection
     * @param username      the username from the pre-login event
     * @param action        the effective anti-bot action (third-party handlers
     *                      may have cancelled the original one)
     * @param continuation  the event continuation to resume
     * @param resumed       per-event exactly-once resume guard (0.5.0/R2)
     */
    private void applyAntiBotDecision(PreLoginEvent preLoginEvent, InboundConnection connection,
                                      String username, Action action, Continuation continuation,
                                      AtomicBoolean resumed) {
        switch (action) {
            case Ignore:
                // FastLogin stops handling the connection — login continues as
                // a normal cracked login without premium handling
                resumeOnce(continuation, resumed);
                break;
            case Block:
                String message = plugin.getCore().getMessage("kick-antibot");
                TextComponent messageParsed = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
                preLoginEvent.setResult(PreLoginComponentResult.denied(messageParsed));
                resumeOnce(continuation, resumed);
                break;
            case Continue:
            default:
                // third-party handler cancelled the anti-bot action — run the
                // premium check off the event loop and resume when it completes
                plugin.getScheduler().runAsync(() -> {
                    try {
                        new AsyncPremiumCheck(plugin, connection, username, preLoginEvent).run();
                    } catch (Exception runEx) {
                        plugin.getLog().error("Error during premium check", runEx);
                    } finally {
                        // same guard as the outer paths: at most one resume (0.5.0/R2)
                        resumeOnce(continuation, resumed);
                    }
                });
                break;
        }
    }

    /**
     * 0.5.0/R2: apply the decision and guarantee that the continuation is
     * resumed even when applying it throws — otherwise the login hangs until
     * the read timeout instead of degrading to a normal login.
     *
     * @param preLoginEvent the paused pre-login event
     * @param connection    the inbound connection
     * @param username      the username from the pre-login event
     * @param action        the effective anti-bot action
     * @param continuation  the event continuation to resume
     * @param resumed       per-event exactly-once guard
     */
    void applyDecisionSafely(PreLoginEvent preLoginEvent, InboundConnection connection,
                             String username, Action action, Continuation continuation,
                             AtomicBoolean resumed) {
        try {
            applyAntiBotDecision(preLoginEvent, connection, username, action, continuation, resumed);
        } catch (Exception decisionEx) {
            plugin.getLog().error("Error applying anti-bot decision for {}", username, decisionEx);
            resumeOnce(continuation, resumed);
        }
    }

    /**
     * 0.5.0/R2: resume the continuation at most once per event.
     *
     * @param continuation the event continuation
     * @param resumed      CAS guard; flipped to true on the first call
     */
    static void resumeOnce(Continuation continuation, AtomicBoolean resumed) {
        if (resumed.compareAndSet(false, true)) {
            continuation.resume();
        }
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        if (event.isOnlineMode()) {
            LoginSession session = plugin.getSession().get(event.getConnection().getRemoteAddress());
            if (session == null) {
                plugin.getLog().error("No active login session found for onlinemode player {}", event.getUsername());
                return;
            }

            UUID verifiedUUID = event.getGameProfile().getId();
            String verifiedUsername = event.getUsername();
            session.setUuid(verifiedUUID);
            session.setVerifiedUsername(verifiedUsername);

            StoredProfile playerProfile = session.getProfile();
            playerProfile.setId(verifiedUUID);
            if (!(boolean) plugin.getCore().getConfig().get("premiumUuid")) {
                UUID offlineUUID = UUIDAdapter.generateOfflineId(event.getUsername());
                event.setGameProfile(event.getGameProfile().withId(offlineUUID));
                plugin.getLog().info("Overridden UUID from {} to {} (based of {}) on {}",
                        verifiedUUID, offlineUUID, verifiedUsername, event.getConnection());
            }

            if (!(boolean) plugin.getCore().getConfig().get("forwardSkin")) {
                List<Property> newProp = removeSkin(event.getGameProfile().getProperties());
                event.setGameProfile(event.getGameProfile().withProperties(newProp));
            }
        }
    }

    private List<GameProfile.Property> removeSkin(Collection<Property> oldProperties) {
        List<GameProfile.Property> newProperties = new ArrayList<>(oldProperties.size());
        for (GameProfile.Property property : oldProperties) {
            if (!"textures".equals(property.getName())) {
                newProperties.add(property);
            }
        }

        return newProperties;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent serverConnectedEvent) {
        Player player = serverConnectedEvent.getPlayer();
        RegisteredServer server = serverConnectedEvent.getServer();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (floodgateService != null) {
            FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(player.getUniqueId());
            if (floodgatePlayer != null) {
                plugin.getLog().info("Running floodgate handling for {}", player);
                Runnable floodgateAuthTask = new FloodgateAuthTask(plugin.getCore(), player, floodgatePlayer, server);
                plugin.getScheduler().runAsync(floodgateAuthTask);
                return;
            }
        }

        VelocityLoginSession session = plugin.getSession().get(player.getRemoteAddress());
        if (session == null) {
            plugin.getLog().info("No active login session found on server connect for {}", player);
            return;
        }

        // delay sending force command, because Paper will process the login event asynchronously
        // In this case it means that the force command (plugin message) is already received and processed while
        // player is still in the login phase and reported to be offline.
        Runnable loginTask = new ForceLoginTask(plugin.getCore(), player, server, session);

        // Delay at least one second, otherwise the login command can be missed
        plugin.getScheduler().runAsyncDelayed(loginTask, Duration.ofSeconds(1));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        Player player = disconnectEvent.getPlayer();
        plugin.getCore().getPendingConfirms().remove(player.getUniqueId());

        plugin.getSession().remove(player.getRemoteAddress());
    }

    /**
     * Get the Floodgate username from the Floodgate plugin's playerCache using lots of reflections
     *
     * @param connection
     * @return the Floodgate username or null if not found
     */
    private String getFloodgateUsername(InboundConnection connection) {
        try {
            // get floodgate's event handler
            Object floodgateEventHandler = getFloodgateHandler();
            if (floodgateEventHandler == null) {
                return null;
            }

            // Get the Floodgate playerCache field
            Field playerCacheField = floodgateEventHandler.getClass().getDeclaredField("playerCache");
            playerCacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Cache<InboundConnection, FloodgatePlayer> playerCache =
                    (Cache<InboundConnection, FloodgatePlayer>) playerCacheField.get(floodgateEventHandler);

            // Find the FloodgatePlayer instance in playerCache
            FloodgatePlayer floodgatePlayer = playerCache.getIfPresent(connection);
            if (floodgatePlayer == null) {
                return null;
            }

            return floodgatePlayer.getCorrectUsername();
        } catch (Exception ex) {
            plugin.getLog().error("Failed to fetch current floodgate username", ex);
        }

        return null;
    }

    private Object getFloodgateHandler()
            throws NoSuchFieldException, IllegalAccessException {
        // Get Velocity's event manager
        EventManager eventManager = plugin.getServer().getEventManager();
        Field handlerField = eventManager.getClass().getDeclaredField("handlersByType");
        handlerField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ListMultimap<Class<?>, ?> handlersByType = (ListMultimap<Class<?>, ?>) handlerField.get(eventManager);

        // Get all registered PreLoginEvent handlers
        List<?> loginEventRegistrations = handlersByType.get(PreLoginEvent.class);
        Field pluginField = loginEventRegistrations.get(0).getClass().getDeclaredField("plugin");
        pluginField.setAccessible(true);

        // Find the Floodgate plugin's PreLoginEvent handler registration (Velocity implementation)
        Object floodgateRegistration = null;
        for (Object handler : loginEventRegistrations) {
            PluginContainer eventHandlerPlugin = (PluginContainer) pluginField.get(handler);
            String eventHandlerPluginName = eventHandlerPlugin.getInstance().get().getClass().getName();
            if (eventHandlerPluginName.equals(FLOODGATE_PLUGIN_NAME)) {
                floodgateRegistration = handler;
                break;
            }
        }

        if (floodgateRegistration == null) {
            return null;
        }

        // Extract the EventHandler instance (floodgate impl) from Velocity's internal registration handler storage
        Field eventHandlerField = floodgateRegistration.getClass().getDeclaredField("instance");
        eventHandlerField.setAccessible(true);
        return eventHandlerField.get(floodgateRegistration);
    }
}
