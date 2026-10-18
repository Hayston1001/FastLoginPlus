/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.ToggleWire;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Sends the premium-toggle consent to the proxy, riding the player connection
 * the way the classic backends do. After any required confirmation, the proxy
 * records the consent and resolves the target Mojang UUID. A later verified
 * connection with recorded consent can enable premium login without erasing the password.
 *
 * <p>The payload carries the sending backend's trusted proxy IDs so the proxy
 * authenticates the message exactly like the classic backends' messages.
 */
final class BindingConsentWriter {

    /** Raw toggle payload, kept opaque: the proxy, not the client, parses it. */
    record ConsentPayload(byte[] data) implements CustomPacketPayload {

        static final CustomPacketPayload.Type<ConsentPayload> TYPE = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("fastloginplus", ToggleWire.CHANNEL));

        static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ConsentPayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeBytes(payload.data()),
                        buffer -> {
                            byte[] data = new byte[buffer.readableBytes()];
                            buffer.readBytes(data);
                            return new ConsentPayload(data);
                        });

        @Override
        public CustomPacketPayload.Type<ConsentPayload> type() {
            return TYPE;
        }
    }

    private BindingConsentWriter() {
    }

    /** Registers the channel once, so consent payloads can be sent on it. */
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ConsentPayload.TYPE, ConsentPayload.CODEC);
    }

    /**
     * Declares the premium-upgrade consent of one player name to the proxy.
     *
     * @param player         the connection that carries the message
     * @param playerName     exact name the consent is for
     * @param sourceProxyIds comma-joined proxy IDs this backend trusts
     */
    static void send(ServerPlayer player, String playerName, String sourceProxyIds) {
        send(player, playerName, sourceProxyIds, true);
    }

    /**
     * Admin consent skips premium-warning, matching the classic other-player command.
     *
     * @param carrier        the connection carrying the message
     * @param targetName     exact target name, independent of the carrier
     * @param sourceProxyIds comma-joined proxy IDs this backend trusts
     */
    static void sendForOther(ServerPlayer carrier, String targetName, String sourceProxyIds) {
        send(carrier, targetName, sourceProxyIds, false);
    }

    private static void send(ServerPlayer player, String playerName, String sourceProxyIds, boolean sourceInvoker) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            // Only the self path uses the proxy's premium-warning confirmation gate.
            ToggleWire.write(output, true, playerName, sourceInvoker, sourceProxyIds);
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot serialize the premium consent message", impossible);
        }
        ServerPlayNetworking.send(player, new ConsentPayload(buffer.toByteArray()));
    }
}
