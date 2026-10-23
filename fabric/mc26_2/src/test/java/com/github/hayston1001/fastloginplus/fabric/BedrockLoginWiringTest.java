/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BedrockLoginWiringTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void verifiedClaimUsesOnlyBedrockBridgeOnceForExactActualPlayer() {
        var bridge = mock(EasyAuth344Bridge.class);
        var notice = mock(FabricCrackedNotice.class);
        var server = mock(MinecraftServer.class);
        var connection = mock(Connection.class);
        var context = mock(PacketContext.class);
        when(context.orElseThrow(PacketContext.CONNECTION)).thenReturn(connection);
        var profile = FabricBedrockLoginTest.profile(FabricBedrockLoginTest.claim(false, "true", System.currentTimeMillis()));
        var owner = FabricBedrockLoginTest.owner();
        when(bridge.beforeBedrock(server, profile.name(), profile.id())).thenReturn(owner);
        var listener = mock(ServerConfigurationPacketListenerImpl.class);
        when(listener.getOwner()).thenReturn(profile);
        when(listener.getPacketContext()).thenReturn(context);
        var handler = mock(ServerGamePacketListenerImpl.class);
        handler.player = mock(ServerPlayer.class);
        when(handler.player.getGameProfile()).thenReturn(profile);
        when(handler.getPacketContext()).thenReturn(context);
        try (var config = mockStatic(FabricConfig.class); var messages = mockStatic(FabricMessages.class)) {
            config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(
                    FabricBedrockLoginTest.PROXY.toString(), false, 24, false));
            FastLoginFabric.beforeConfigure(listener, server, bridge, notice);
            FastLoginFabric.onJoin(handler, bridge, notice);
            FastLoginFabric.onJoin(handler, bridge, notice);
            verify(bridge).beforeBedrock(server, profile.name(), profile.id());
            verify(bridge).onBedrockJoin(handler.player, owner);
            verifyNoMoreInteractions(bridge);
            verifyNoInteractions(notice);
            verify(listener, never()).disconnect(any(Component.class));
            verify(handler, never()).disconnect(any(Component.class));
            messages.verifyNoInteractions();

            // A replacement Player on the same connection cannot consume the completed attempt.
            handler.player = mock(ServerPlayer.class);
            when(handler.player.getGameProfile()).thenReturn(profile);
            FastLoginFabric.onJoin(handler, bridge, notice);
            verify(bridge, never()).onBedrockJoin(handler.player, owner);
            verify(handler).disconnect(org.mockito.ArgumentMatchers.<Component>any());
        }
    }

    @Test
    void signedClaimWithoutCapturedConnectionCannotFallBackToJava() {
        var bridge = mock(EasyAuth344Bridge.class);
        var notice = mock(FabricCrackedNotice.class);
        var handler = mock(ServerGamePacketListenerImpl.class);
        handler.player = mock(ServerPlayer.class);
        when(handler.player.getGameProfile()).thenReturn(FabricBedrockLoginTest.profile(
                FabricBedrockLoginTest.claim(false, "true", System.currentTimeMillis())));
        var context = mock(PacketContext.class);
        when(context.orElseThrow(PacketContext.CONNECTION)).thenReturn(mock(Connection.class));
        when(handler.getPacketContext()).thenReturn(context);
        try (var messages = mockStatic(FabricMessages.class)) {
            FastLoginFabric.onJoin(handler, bridge, notice);
            verify(handler).disconnect(org.mockito.ArgumentMatchers.<Component>any());
            verifyNoInteractions(bridge, notice);
        }
    }
}
