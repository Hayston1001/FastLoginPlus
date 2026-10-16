/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeWire;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Announces backend-local rendering first; only a proxy handoff queues the chat. */
final class FabricCrackedNotice {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fastloginplus_fabric");
    private final ConcurrentMap<Connection, CrackedNoticeQueue> queues = new ConcurrentHashMap<>();

    record Payload(byte[] data) implements CustomPacketPayload {
        static final Type<Payload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("fastloginplus", CrackedNoticeWire.CHANNEL));
        static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBytes(payload.data()),
                buffer -> {
                    if (buffer.readableBytes() > CrackedNoticeWire.MAX_BYTES) {
                        buffer.skipBytes(buffer.readableBytes());
                        return new Payload(new byte[0]);
                    }
                    byte[] data = new byte[buffer.readableBytes()];
                    buffer.readBytes(data);
                    return new Payload(data);
                });

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Payload.TYPE, Payload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Payload.TYPE, Payload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> {
            Connection connection = context.packetContext().orElseThrow(PacketContext.CONNECTION);
            context.server().execute(() -> receive(connection, context.packetListener().getOwner(), payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Connection connection = context.packetContext().orElseThrow(PacketContext.CONNECTION);
            context.server().execute(() -> {
                receive(connection, player.getGameProfile(), payload);
                drain(player, queues.get(connection));
            });
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) ->
                queues.remove(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                queues.remove(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
    }

    void beforeConfigure(ServerConfigurationPacketListenerImpl listener, IdentityClaim claim) {
        if (claim.getDecision() != IdentityClaim.Decision.OFFLINE || !claim.isSameNameCracked()) {
            return;
        }
        try {
            Connection connection = listener.getPacketContext().orElseThrow(PacketContext.CONNECTION);
            CrackedNoticeQueue queue = new CrackedNoticeQueue(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                    claim.getPlayerName(), claim.getForwardedUuid(), FabricConfig.startup().allowedProxyIds(),
                    UUID.randomUUID()));
            queues.put(connection, queue);
            ServerConfigurationNetworking.send(listener, new Payload(queue.capability().encode()));
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not announce backend-local cracked notice support for {}", claim.getPlayerName());
        }
    }

    private void receive(Connection connection, com.mojang.authlib.GameProfile profile, Payload payload) {
        CrackedNoticeQueue queue = queues.get(connection);
        if (queue == null) {
            return;
        }
        try {
            queue.accept(CrackedNoticeWire.decode(payload.data()), FastLoginFabric.parseClaim(profile));
        } catch (IOException | IllegalArgumentException malformed) {
            // Notification-only input never changes authentication or rejects a player.
        }
    }

    void onJoin(ServerPlayer player, IdentityClaim claim) {
        try {
            CrackedNoticeQueue queue = queues.get(player.connection.getPacketContext()
                    .orElseThrow(PacketContext.CONNECTION));
            if (queue != null) {
                queue.joined(claim);
                drain(player, queue);
            }
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not deliver backend-local cracked notice for {}", claim.getPlayerName());
        }
    }

    private void drain(ServerPlayer player, CrackedNoticeQueue queue) {
        if (player.connection.isAcceptingMessages() && queue != null && queue.take()) {
            try {
                player.sendSystemMessage(FabricMessages.text("same-name-cracked-recorded"));
            } catch (RuntimeException unavailable) {
                LOGGER.warn("Could not send backend-local cracked notice for {}", player.getGameProfile().name());
            }
        }
    }
}
