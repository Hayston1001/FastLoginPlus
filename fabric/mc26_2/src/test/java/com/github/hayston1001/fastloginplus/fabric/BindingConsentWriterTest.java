/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.ToggleWire;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class BindingConsentWriterTest {
    @Test
    void otherPlayerPayloadNamesTheTargetAndSkipsInvokerConfirmation() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ServerPlayer carrier = mock(ServerPlayer.class);
        String proxyIds = UUID.randomUUID().toString();
        AtomicReference<BindingConsentWriter.ConsentPayload> sent = new AtomicReference<>();
        try (var networking = mockStatic(ServerPlayNetworking.class)) {
            networking.when(() -> ServerPlayNetworking.send(eq(carrier),
                    any(BindingConsentWriter.ConsentPayload.class))).thenAnswer(call -> {
                        sent.set(call.getArgument(1));
                        return null;
                    });

            BindingConsentWriter.sendForOther(carrier, "OtherUser", proxyIds);

            assertNotNull(sent.get());
            assertEquals("fastloginplus:ch-st", sent.get().type().id().toString());
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(sent.get().data()))) {
                ToggleWire.Toggle toggle = ToggleWire.read(input);
                assertTrue(toggle.willEnable());
                assertEquals("OtherUser", toggle.playerName());
                assertFalse(toggle.isSourceInvoker());
                assertEquals(proxyIds, toggle.sourceProxyId());
                assertEquals(0, input.available());
            }
            networking.verify(() -> ServerPlayNetworking.send(carrier, sent.get()));
            networking.verifyNoMoreInteractions();
        }
    }

    @Test
    void sentPayloadIdentifiesThePlayerAsTheInvoker() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ServerPlayer player = mock(ServerPlayer.class);
        String proxyIds = UUID.randomUUID() + "," + UUID.randomUUID();
        AtomicReference<BindingConsentWriter.ConsentPayload> sent = new AtomicReference<>();
        try (var networking = mockStatic(ServerPlayNetworking.class)) {
            networking.when(() -> ServerPlayNetworking.send(eq(player),
                    any(BindingConsentWriter.ConsentPayload.class))).thenAnswer(call -> {
                        sent.set(call.getArgument(1));
                        return null;
                    });

            BindingConsentWriter.send(player, "TestUser", proxyIds);

            assertNotNull(sent.get());
            assertEquals("fastloginplus:ch-st", sent.get().type().id().toString());
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(sent.get().data()))) {
                ToggleWire.Toggle toggle = ToggleWire.read(input);
                assertTrue(toggle.willEnable());
                assertEquals("TestUser", toggle.playerName());
                assertTrue(toggle.isSourceInvoker());
                assertEquals(proxyIds, toggle.sourceProxyId());
                assertEquals(0, input.available());
            }
            networking.verify(() -> ServerPlayNetworking.send(player, sent.get()));
            networking.verifyNoMoreInteractions();
        }
    }
}
