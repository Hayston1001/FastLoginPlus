/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.task.FloodgateAuthTask;
import com.google.common.cache.Cache;
import com.google.common.collect.ListMultimap;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Keeps every Floodgate-typed member out of the classes registered with Velocity's event
 * manager. That manager inspects listener method signatures, so a listener declaring a
 * Floodgate type fails to register when Floodgate is not installed. This class is only
 * loaded after {@link FastLoginVelocity#hasFloodgate()} returned true.
 */
final class FloodgateAccess {

    private static final String FLOODGATE_PLUGIN_NAME = "org.geysermc.floodgate.VelocityPlugin";

    private FloodgateAccess() {
    }

    /**
     * Reads the corrected username Floodgate assigned to this connection.
     *
     * @param plugin the FastLoginPlus proxy plugin
     * @param connection the incoming connection
     * @return the corrected username, or null when this connection is not a Floodgate player
     */
    static String correctUsername(FastLoginVelocity plugin, InboundConnection connection) {
        FloodgatePlayer player = player(plugin, connection);
        return player == null ? null : player.getCorrectUsername();
    }

    /**
     * Resolves the Floodgate link state for a connection, preferring the cached connection entry.
     *
     * @param plugin the FastLoginPlus proxy plugin
     * @param connection the incoming connection
     * @param uuid the player's UUID
     * @return whether the player is linked, or null when no Floodgate player matches
     */
    static Boolean linked(FastLoginVelocity plugin, InboundConnection connection, UUID uuid) {
        FloodgatePlayer player = player(plugin, connection);
        if (player == null) {
            player = plugin.getFloodgateService().getBedrockPlayer(uuid);
        }
        return player == null ? null : player.getLinkedPlayer() != null;
    }

    /**
     * Resolves the Floodgate link state for a player that is already connected.
     *
     * @param plugin the FastLoginPlus proxy plugin
     * @param uuid the player's UUID
     * @return whether the player is linked, or null when no Floodgate player matches
     */
    static Boolean linked(FastLoginVelocity plugin, UUID uuid) {
        FloodgatePlayer player = plugin.getFloodgateService().getBedrockPlayer(uuid);
        return player == null ? null : player.getLinkedPlayer() != null;
    }

    /**
     * Runs Floodgate authentication for a player that just connected to a backend.
     *
     * @param plugin the FastLoginPlus proxy plugin
     * @param player the connected player
     * @param server the server the player joined
     * @return true when Floodgate handling was scheduled and the caller must stop
     */
    static boolean authenticate(FastLoginVelocity plugin, Player player, RegisteredServer server) {
        FloodgatePlayer bedrock = plugin.getFloodgateService().getBedrockPlayer(player.getUniqueId());
        if (bedrock == null) {
            return false;
        }
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Running floodgate handling for {}", player);
        }
        plugin.getScheduler().runAsync(new FloodgateAuthTask(plugin.getCore(), player, bedrock, server));
        return true;
    }

    private static FloodgatePlayer player(FastLoginVelocity plugin, InboundConnection connection) {
        try {
            Object floodgateEventHandler = handler(plugin);
            if (floodgateEventHandler == null) {
                return null;
            }

            Field playerCacheField = floodgateEventHandler.getClass().getDeclaredField("playerCache");
            playerCacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Cache<InboundConnection, FloodgatePlayer> playerCache =
                    (Cache<InboundConnection, FloodgatePlayer>) playerCacheField.get(floodgateEventHandler);
            return playerCache.getIfPresent(connection);
        } catch (Exception ex) {
            plugin.getLog().error("Failed to fetch current floodgate player", ex);
            return null;
        }
    }

    private static Object handler(FastLoginVelocity plugin)
            throws NoSuchFieldException, IllegalAccessException {
        EventManager eventManager = plugin.getServer().getEventManager();
        Field handlerField = eventManager.getClass().getDeclaredField("handlersByType");
        handlerField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ListMultimap<Class<?>, ?> handlersByType = (ListMultimap<Class<?>, ?>) handlerField.get(eventManager);

        List<?> loginEventRegistrations = handlersByType.get(PreLoginEvent.class);
        Field pluginField = loginEventRegistrations.get(0).getClass().getDeclaredField("plugin");
        pluginField.setAccessible(true);

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

        Field eventHandlerField = floodgateRegistration.getClass().getDeclaredField("instance");
        eventHandlerField.setAccessible(true);
        return eventHandlerField.get(floodgateRegistration);
    }
}
