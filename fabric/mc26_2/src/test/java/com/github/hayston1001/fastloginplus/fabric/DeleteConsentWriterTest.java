/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class DeleteConsentWriterTest {
    @Test
    void registersClientboundPlayPayloadType() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        @SuppressWarnings("unchecked")
        PayloadTypeRegistry<FriendlyByteBuf> registry = mock(PayloadTypeRegistry.class);
        try (var registries = mockStatic(PayloadTypeRegistry.class)) {
            registries.when(PayloadTypeRegistry::clientboundPlay).thenReturn(registry);
            DeleteConsentWriter.register();
            org.mockito.Mockito.verify(registry).register(
                    DeleteConsentWriter.DeletePayload.TYPE, DeleteConsentWriter.DeletePayload.CODEC);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void payloadExactlyMatchesProxyDeletePremiumMessageLayout(boolean sourceInvoker) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ServerPlayer carrier = mock(ServerPlayer.class);
        String proxyIds = "00000000-0000-0000-0000-000000000001,00000000-0000-0000-0000-000000000002";
        AtomicReference<DeleteConsentWriter.DeletePayload> sent = new AtomicReference<>();
        try (var networking = mockStatic(ServerPlayNetworking.class)) {
            networking.when(() -> ServerPlayNetworking.send(eq(carrier), any(DeleteConsentWriter.DeletePayload.class)))
                    .thenAnswer(call -> { sent.set(call.getArgument(1)); return null; });
            DeleteConsentWriter.send(carrier, "TargetUser", sourceInvoker, proxyIds);
            assertNotNull(sent.get());
            assertEquals("fastloginplus:del-st", sent.get().type().id().toString());
            ByteArrayOutputStream expected = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(expected)) {
                output.writeUTF("TargetUser");
                output.writeBoolean(sourceInvoker);
                output.writeUTF(proxyIds);
            }
            assertArrayEquals(expected.toByteArray(), sent.get().data());
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(sent.get().data()))) {
                assertEquals("TargetUser", input.readUTF());
                assertEquals(sourceInvoker, input.readBoolean());
                assertEquals(proxyIds, input.readUTF());
                assertEquals(0, input.available());
            }
            networking.verify(() -> ServerPlayNetworking.send(carrier, sent.get()));
            networking.verifyNoMoreInteractions();
        }
    }
}
