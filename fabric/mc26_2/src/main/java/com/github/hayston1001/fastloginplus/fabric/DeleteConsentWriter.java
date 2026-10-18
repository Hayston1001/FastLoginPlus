/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Requests deletion of a proxy FLP profile, never an EasyAuth account. */
final class DeleteConsentWriter {

    record DeletePayload(byte[] data) implements CustomPacketPayload {
        static final Type<DeletePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("fastloginplus", "del-st"));
        static final StreamCodec<FriendlyByteBuf, DeletePayload> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBytes(payload.data()), buffer -> {
                    byte[] data = new byte[buffer.readableBytes()];
                    buffer.readBytes(data);
                    return new DeletePayload(data);
                });

        @Override
        public Type<DeletePayload> type() {
            return TYPE;
        }
    }

    private DeleteConsentWriter() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(DeletePayload.TYPE, DeletePayload.CODEC);
    }

    static void send(ServerPlayer carrier, String targetName, boolean sourceInvoker, String sourceProxyIds) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeUTF(targetName);
            output.writeBoolean(sourceInvoker);
            output.writeUTF(sourceProxyIds);
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot serialize the profile deletion request", impossible);
        }
        ServerPlayNetworking.send(carrier, new DeletePayload(buffer.toByteArray()));
    }
}
