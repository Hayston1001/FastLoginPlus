/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeClaim;
import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeWire;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.mojang.authlib.GameProfile;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;
import java.util.function.Consumer;

/** Renders forwarded notices locally and announces server rendering independently of notification debt. */
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
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) ->
                queues.remove(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                queues.remove(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION)));
    }

    static CrackedNoticeClaim readNotice(GameProfile profile) {
        return CrackedNoticeClaim.parse(profile.properties().get(CrackedNoticeClaim.PROPERTY_NAME).stream()
                .map(com.mojang.authlib.properties.Property::value).toList(), profile.name(), profile.id());
    }

    void beforeConfigure(ServerConfigurationPacketListenerImpl listener, IdentityClaim identity) {
        try {
            // Every validated connection refreshes the proxy's server marker, even without a pending notice.
            announce(identity, bytes -> ServerConfigurationNetworking.send(listener, new Payload(bytes)),
                    FabricConfig.startup().allowedProxyIds());
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not announce backend-local notice rendering for {}", identity.getPlayerName());
        }
        try {
            CrackedNoticeClaim notice = readNotice(listener.getOwner());
            if (notice != null && notice.accepts(identity)) {
                queues.putIfAbsent(listener.getPacketContext().orElseThrow(PacketContext.CONNECTION),
                        new CrackedNoticeQueue(notice));
            }
        } catch (RuntimeException unavailable) {
            // Optional notification data must never reject or authenticate a connection.
            LOGGER.warn("Could not read backend-local cracked notice for {}", identity.getPlayerName());
        }
    }

    static void announce(IdentityClaim identity, Consumer<byte[]> send, String proxyIds) {
        send.accept(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE, identity.getPlayerName(),
                identity.getForwardedUuid(), proxyIds, UUID.randomUUID()).encode());
    }

    void onJoin(ServerPlayer player, IdentityClaim identity) {
        try {
            CrackedNoticeQueue queue = queues.get(player.connection.getPacketContext()
                    .orElseThrow(PacketContext.CONNECTION));
            if (queue != null && player.connection.isAcceptingMessages()) {
                renderAfterJoin(queue, identity, player::sendSystemMessage,
                        bytes -> ServerPlayNetworking.send(player, new Payload(bytes)),
                        FabricConfig.startup().allowedProxyIds());
            }
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not deliver or acknowledge backend-local cracked notice for {}",
                    identity.getPlayerName());
        }
    }

    static void renderAfterJoin(CrackedNoticeQueue queue, IdentityClaim identity,
                                Consumer<net.minecraft.network.chat.Component> render,
                                Consumer<byte[]> acknowledge, String proxyIds) {
        queue.renderAfterJoin(identity,
                () -> render.accept(FabricMessages.text("same-name-cracked-recorded")),
                () -> acknowledge.accept(queue.acknowledgement(proxyIds).encode()));
    }
}
