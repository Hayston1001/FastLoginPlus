/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.mojang.authlib.GameProfile;
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
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FabricLoginClassificationWiringTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bothStagesKeepTheSpecificPolicyMessage() {
        Component expected = FabricMessages.text("ownership-conflict");
        try (var config = mockStatic(FabricConfig.class)) {
            config.when(FabricConfig::startup).thenReturn(settings());

            var configureBridge = mock(EasyAuth344Bridge.class);
            var configureConnection = mock(Connection.class);
            var configureProfile = profile();
            when(configureBridge.beforeBedrock(any(), any(), any()))
                    .thenThrow(FabricMessages.failure("ownership-conflict"));
            var listener = listener(configureProfile, configureConnection);
            FastLoginFabric.beforeConfigure(listener, mock(MinecraftServer.class), configureBridge,
                    mock(FabricCrackedNotice.class));
            assertEquals(expected.getString(), disconnected(listener));

            var joinBridge = mock(EasyAuth344Bridge.class);
            var joinConnection = mock(Connection.class);
            var joinProfile = profile();
            var owner = FabricBedrockLoginTest.owner();
            when(joinBridge.beforeBedrock(any(), any(), any())).thenReturn(owner);
            doThrow(FabricMessages.failure("ownership-conflict"))
                    .when(joinBridge).onBedrockJoin(any(), any());
            var handler = handler(joinProfile, joinConnection);
            FastLoginFabric.beforeConfigure(listener(joinProfile, joinConnection), mock(MinecraftServer.class),
                    joinBridge, mock(FabricCrackedNotice.class));
            FastLoginFabric.onJoin(handler, joinBridge, mock(FabricCrackedNotice.class));
            assertEquals(expected.getString(), disconnected(handler));
        }
    }

    @Test
    void bothStagesClassifyStorageFailuresAsAuthUnavailable() {
        Component expected = FabricMessages.text("auth-unavailable");
        try (var config = mockStatic(FabricConfig.class)) {
            config.when(FabricConfig::startup).thenReturn(settings());

            var configureBridge = mock(EasyAuth344Bridge.class);
            var configureProfile = profile();
            when(configureBridge.beforeBedrock(any(), any(), any()))
                    .thenThrow(new AuthUnavailableException("SQLITE_BUSY: database is locked"));
            var listener = listener(configureProfile, mock(Connection.class));
            FastLoginFabric.beforeConfigure(listener, mock(MinecraftServer.class), configureBridge,
                    mock(FabricCrackedNotice.class));
            assertEquals(expected.getString(), disconnected(listener));

            var joinBridge = mock(EasyAuth344Bridge.class);
            var joinProfile = profile();
            var joinConnection = mock(Connection.class);
            var owner = FabricBedrockLoginTest.owner();
            when(joinBridge.beforeBedrock(any(), any(), any())).thenReturn(owner);
            doThrow(new AuthUnavailableException("EasyAuth Bedrock login failed"))
                    .when(joinBridge).onBedrockJoin(any(), any());
            var handler = handler(joinProfile, joinConnection);
            FastLoginFabric.beforeConfigure(listener(joinProfile, joinConnection), mock(MinecraftServer.class),
                    joinBridge, mock(FabricCrackedNotice.class));
            FastLoginFabric.onJoin(handler, joinBridge, mock(FabricCrackedNotice.class));
            assertEquals(expected.getString(), disconnected(handler));
        }
    }

    @Test
    void configureRejectsMalformedBedrockClaimAsInvalidIdentity() {
        Component expected = FabricMessages.text("invalid-identity");
        var bridge = mock(EasyAuth344Bridge.class);
        var listener = listener(FabricBedrockLoginTest.profile("unsigned"), mock(Connection.class));

        try (var config = mockStatic(FabricConfig.class)) {
            config.when(FabricConfig::startup).thenReturn(settings());
            FastLoginFabric.beforeConfigure(listener, mock(MinecraftServer.class), bridge,
                    mock(FabricCrackedNotice.class));
        }

        assertEquals(expected.getString(), disconnected(listener));
        verifyNoInteractions(bridge);
    }

    private static FabricConfig.StartupSettings settings() {
        return new FabricConfig.StartupSettings(FabricBedrockLoginTest.PROXY.toString(), false, 24, false);
    }

    private static GameProfile profile() {
        return FabricBedrockLoginTest.profile(FabricBedrockLoginTest.claim(false, "true", System.currentTimeMillis()));
    }

    private static ServerConfigurationPacketListenerImpl listener(GameProfile profile, Connection connection) {
        var context = mock(PacketContext.class);
        when(context.orElseThrow(PacketContext.CONNECTION)).thenReturn(connection);
        var listener = mock(ServerConfigurationPacketListenerImpl.class);
        when(listener.getOwner()).thenReturn(profile);
        when(listener.getPacketContext()).thenReturn(context);
        return listener;
    }

    private static ServerGamePacketListenerImpl handler(GameProfile profile, Connection connection) {
        var context = mock(PacketContext.class);
        when(context.orElseThrow(PacketContext.CONNECTION)).thenReturn(connection);
        var handler = mock(ServerGamePacketListenerImpl.class);
        handler.player = mock(ServerPlayer.class);
        when(handler.player.getGameProfile()).thenReturn(profile);
        when(handler.getPacketContext()).thenReturn(context);
        return handler;
    }

    private static String disconnected(ServerConfigurationPacketListenerImpl listener) {
        ArgumentCaptor<Component> reason = ArgumentCaptor.forClass(Component.class);
        verify(listener).disconnect(reason.capture());
        verify(listener, never()).disconnect((Component) null);
        return reason.getValue().getString();
    }

    private static String disconnected(ServerGamePacketListenerImpl handler) {
        ArgumentCaptor<Component> reason = ArgumentCaptor.forClass(Component.class);
        verify(handler).disconnect(reason.capture());
        verify(handler, never()).disconnect((Component) null);
        return reason.getValue().getString();
    }
}
