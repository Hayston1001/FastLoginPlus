/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BedrockBoundaryWiringTest {
    private final EasyAuth344Bridge bridge = mock(EasyAuth344Bridge.class);
    private final FabricCrackedNotice notice = mock(FabricCrackedNotice.class);
    private final MinecraftServer server = mock(MinecraftServer.class);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static Stream<GameProfile> bedrockProfiles() {
        // Neither profile has a claim. The refusal must precede parsing and EasyAuth policy checks.
        return Stream.of(new GameProfile(UUID.fromString("00000000-0000-0000-0009-01eed05d107b"), "Name"),
                new GameProfile(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), ".Name"));
    }

    static Stream<GameProfile> javaProfiles() {
        String name = "Name";
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        UUID premium = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        return Stream.of(withClaim(new IdentityClaim(IdentityClaim.Decision.OFFLINE, name, offline, null)),
                withClaim(new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, name, premium, premium)),
                withClaim(new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, name, offline, premium)));
    }

    @ParameterizedTest
    @MethodSource("bedrockProfiles")
    void configureRefusesBedrockOnceBeforeClaimsAndAccountChecks(GameProfile profile) {
        ServerConfigurationPacketListenerImpl listener = mock(ServerConfigurationPacketListenerImpl.class);
        when(listener.getOwner()).thenReturn(profile);
        Component reason = Component.literal("Bedrock unsupported");
        try (var messages = mockStatic(FabricMessages.class)) {
            messages.when(() -> FabricMessages.text("bedrock-unsupported")).thenReturn(reason);
            FastLoginFabric.beforeConfigure(listener, server, bridge, notice);
            verify(listener).disconnect(reason);
            verify(listener).getOwner();
            verifyNoMoreInteractions(listener);
            messages.verify(() -> FabricMessages.text("bedrock-unsupported"));
            messages.verifyNoMoreInteractions();
            verifyNoInteractions(bridge, notice, server);
        }
    }

    @ParameterizedTest
    @MethodSource("bedrockProfiles")
    void loginFallbackRefusesBedrockOnceBeforeClaimsAndAccountChecks(GameProfile profile) {
        ServerGamePacketListenerImpl handler = handler(profile);
        Component reason = Component.literal("Bedrock unsupported");
        try (var messages = mockStatic(FabricMessages.class)) {
            messages.when(() -> FabricMessages.text("bedrock-unsupported")).thenReturn(reason);
            FastLoginFabric.onJoin(handler, bridge, notice);
            verify(handler).disconnect(reason);
            verifyNoMoreInteractions(handler);
            messages.verify(() -> FabricMessages.text("bedrock-unsupported"));
            messages.verifyNoMoreInteractions();
            verifyNoInteractions(bridge, notice, server);
        }
    }

    @ParameterizedTest
    @MethodSource("javaProfiles")
    void configurePreservesValidJavaClaimsWithoutDisconnecting(GameProfile profile) {
        ServerConfigurationPacketListenerImpl listener = mock(ServerConfigurationPacketListenerImpl.class);
        when(listener.getOwner()).thenReturn(profile);
        try (var messages = mockStatic(FabricMessages.class)) {
            FastLoginFabric.beforeConfigure(listener, server, bridge, notice);
            verify(listener, never()).disconnect(any(Component.class));
            verify(bridge).verifyPolicy();
            var claim = org.mockito.ArgumentCaptor.forClass(IdentityClaim.class);
            verify(bridge).beforeConfigure(org.mockito.ArgumentMatchers.eq(server), claim.capture());
            assertEquals(FastLoginFabric.parseClaim(profile).encode(), claim.getValue().encode());
            verify(notice).beforeConfigure(listener, claim.getValue());
            messages.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @MethodSource("javaProfiles")
    void loginPreservesValidJavaClaimsWithoutDisconnecting(GameProfile profile) {
        ServerGamePacketListenerImpl handler = handler(profile);
        try (var messages = mockStatic(FabricMessages.class)) {
            FastLoginFabric.onJoin(handler, bridge, notice);
            verify(handler, never()).disconnect(any(Component.class));
            var claim = org.mockito.ArgumentCaptor.forClass(IdentityClaim.class);
            verify(bridge).onJoin(org.mockito.ArgumentMatchers.eq(handler.player), claim.capture());
            assertEquals(FastLoginFabric.parseClaim(profile).encode(), claim.getValue().encode());
            verify(notice).onJoin(handler.player, claim.getValue());
            messages.verifyNoInteractions();
        }
    }

    @Test
    void malformedJavaClaimStillUsesInvalidIdentityDuringConfigure() {
        ServerConfigurationPacketListenerImpl listener = mock(ServerConfigurationPacketListenerImpl.class);
        when(listener.getOwner()).thenReturn(new GameProfile(UUID.randomUUID(), "Name"));
        Component reason = Component.literal("Invalid identity");
        try (var messages = mockStatic(FabricMessages.class)) {
            messages.when(() -> FabricMessages.text("invalid-identity")).thenReturn(reason);
            FastLoginFabric.beforeConfigure(listener, server, bridge, notice);
            verify(listener).disconnect(reason);
            messages.verify(() -> FabricMessages.text("invalid-identity"));
            messages.verifyNoMoreInteractions();
            verify(bridge).verifyPolicy();
            verifyNoMoreInteractions(bridge);
            verifyNoInteractions(notice);
        }
    }

    @Test
    void relocatedPolicyStillRejectsArbitraryPremiumUuidRewrite() {
        UUID forwarded = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        UUID premium = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        GameProfile profile = withClaim(new IdentityClaim(
                IdentityClaim.Decision.PREMIUM_VERIFIED, "Name", forwarded, premium));

        assertThrows(IllegalArgumentException.class, () -> FastLoginFabric.parseClaim(profile));
    }

    private static GameProfile withClaim(IdentityClaim claim) {
        var properties = ArrayListMultimap.<String, Property>create();
        properties.put(IdentityClaim.PROPERTY_NAME, new Property(IdentityClaim.PROPERTY_NAME, claim.encode()));
        return new GameProfile(claim.getForwardedUuid(), claim.getPlayerName(), new PropertyMap(properties));
    }

    private ServerGamePacketListenerImpl handler(GameProfile profile) {
        ServerGamePacketListenerImpl handler = mock(ServerGamePacketListenerImpl.class);
        handler.player = mock(ServerPlayer.class);
        when(handler.player.getGameProfile()).thenReturn(profile);
        return handler;
    }
}
