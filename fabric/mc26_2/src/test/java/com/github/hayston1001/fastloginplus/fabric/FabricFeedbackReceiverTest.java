/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class FabricFeedbackReceiverTest {

    private static final UUID PROXY = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void decodesTheClassicFeedbackLayout() throws Exception {
        FabricFeedbackReceiver.Feedback feedback = FabricFeedbackReceiver.decode(payload());

        assertNotNull(feedback);
        assertEquals("TargetUser", feedback.playerName());
        assertEquals("delete-success", feedback.localeId());
        assertEquals(PROXY, feedback.proxyId());
    }

    @Test
    void rejectsTruncatedTrailingAndEmptyPayloads() throws Exception {
        byte[] valid = payload();

        assertNull(FabricFeedbackReceiver.decode(Arrays.copyOf(valid, valid.length - 1)));
        assertNull(FabricFeedbackReceiver.decode(Arrays.copyOf(valid, valid.length + 1)));
        assertNull(FabricFeedbackReceiver.decode(new byte[0]));
    }

    @Test
    void allowlistParsingIgnoresMalformedEntries() {
        String configured = " not-a-uuid, " + PROXY + " ,,";

        assertTrue(FabricFeedbackReceiver.isAllowedProxy(PROXY, configured));
        assertFalse(FabricFeedbackReceiver.isAllowedProxy(UUID.randomUUID(), configured));
        assertFalse(FabricFeedbackReceiver.isAllowedProxy(PROXY, ""));
    }

    @Test
    void typeIdMatchesTheProxyChannel() {
        assertEquals("fastloginplus:fb-st", FabricFeedbackReceiver.FeedbackPayload.TYPE.id().toString());
    }

    @Test
    void registersTheServerboundPayloadTypeAndHandler() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        @SuppressWarnings("unchecked")
        PayloadTypeRegistry<FriendlyByteBuf> registry = mock(PayloadTypeRegistry.class);
        try (var registries = mockStatic(PayloadTypeRegistry.class);
             var networking = mockStatic(ServerPlayNetworking.class)) {
            registries.when(PayloadTypeRegistry::serverboundPlay).thenReturn(registry);
            networking.when(() -> ServerPlayNetworking.registerGlobalReceiver(
                    eq(FabricFeedbackReceiver.FeedbackPayload.TYPE), any())).thenReturn(true);

            FabricFeedbackReceiver.register();

            org.mockito.Mockito.verify(registry).register(
                    FabricFeedbackReceiver.FeedbackPayload.TYPE, FabricFeedbackReceiver.FeedbackPayload.CODEC);
            networking.verify(() -> ServerPlayNetworking.registerGlobalReceiver(
                    eq(FabricFeedbackReceiver.FeedbackPayload.TYPE), any()));
        }
    }

    private static byte[] payload() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeUTF("TargetUser");
            output.writeUTF("delete-success");
            output.writeLong(PROXY.getMostSignificantBits());
            output.writeLong(PROXY.getLeastSignificantBits());
        }
        return buffer.toByteArray();
    }
}
