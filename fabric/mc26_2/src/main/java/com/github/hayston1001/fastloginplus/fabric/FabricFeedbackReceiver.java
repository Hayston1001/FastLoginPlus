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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Receives the result of a relayed toggle/delete command from the proxy and
 * logs it on this backend's console, mirroring the classic
 * {@code ProxyFeedbackListener}.
 *
 * <p>The payload carries a locale key and the proxy's UUID; the key is
 * resolved against this backend's language file and the UUID must be one of
 * the configured {@code allowed-proxy-ids}, the same trust model as the
 * force-login channel. The raw bytes keep the classic
 * {@code ToggleFeedbackMessage} layout (UTF name, UTF locale key, two longs),
 * so the proxy does not need a separate Fabric encoding.</p>
 */
final class FabricFeedbackReceiver {

    static final String CHANNEL = "fb-st";

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    /** Generous cap for a name, a locale key and a UUID. */
    private static final int MAX_BYTES = 4096;

    /** Opaque relay feedback, parsed only by {@link #decode(byte[])}. */
    record FeedbackPayload(byte[] data) implements CustomPacketPayload {

        static final CustomPacketPayload.Type<FeedbackPayload> TYPE = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("fastloginplus", CHANNEL));

        static final StreamCodec<FriendlyByteBuf, FeedbackPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBytes(payload.data()),
                buffer -> {
                    if (buffer.readableBytes() > MAX_BYTES) {
                        buffer.skipBytes(buffer.readableBytes());
                        return new FeedbackPayload(new byte[0]);
                    }
                    byte[] data = new byte[buffer.readableBytes()];
                    buffer.readBytes(data);
                    return new FeedbackPayload(data);
                });

        @Override
        public CustomPacketPayload.Type<FeedbackPayload> type() {
            return TYPE;
        }
    }

    /** One decoded relay result. */
    record Feedback(String playerName, String localeId, UUID proxyId) {
    }

    private FabricFeedbackReceiver() {
    }

    /** Registers the server-bound channel and its handler once. */
    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(FeedbackPayload.TYPE, FeedbackPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FeedbackPayload.TYPE, (payload, context) ->
                receive(payload.data()));
    }

    /**
     * Decodes, authenticates and logs one payload; anything invalid is dropped.
     *
     * @param data raw payload bytes as sent by the proxy
     */
    static void receive(byte[] data) {
        Feedback feedback = decode(data);
        if (feedback == null) {
            LOGGER.warn("Ignoring malformed proxy relay feedback");
            return;
        }
        if (!isAllowedProxy(feedback.proxyId())) {
            LOGGER.warn("Received relay feedback from unknown proxy id: {}", feedback.proxyId());
            return;
        }
        LOGGER.info("Proxy result for {}: {}", feedback.playerName(), FabricMessages.plain(feedback.localeId()));
    }

    /**
     * Parses the classic feedback layout.
     *
     * @param data raw payload bytes
     * @return the decoded feedback, or {@code null} when the payload is malformed
     */
    static Feedback decode(byte[] data) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            String playerName = input.readUTF();
            String localeId = input.readUTF();
            UUID proxyId = new UUID(input.readLong(), input.readLong());
            if (input.available() != 0 || playerName.isEmpty() || localeId.isEmpty()) {
                return null;
            }
            return new Feedback(playerName, localeId, proxyId);
        } catch (IOException | RuntimeException malformed) {
            return null;
        }
    }

    static boolean isAllowedProxy(UUID proxyId) {
        try {
            return isAllowedProxy(proxyId, FabricConfig.startup().allowedProxyIds());
        } catch (RuntimeException unavailable) {
            // no readable configuration means no trusted proxy
            return false;
        }
    }

    /**
     * Parses the comma-joined allowlist; malformed entries are ignored.
     *
     * @param proxyId the proxy claimed by the payload
     * @param configured comma-joined {@code allowed-proxy-ids}
     * @return true when the ID is listed
     */
    static boolean isAllowedProxy(UUID proxyId, String configured) {
        for (String value : configured.split(",")) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                if (UUID.fromString(trimmed).equals(proxyId)) {
                    return true;
                }
            } catch (IllegalArgumentException malformedEntry) {
                // a broken entry must not make another entry match
            }
        }
        return false;
    }
}
