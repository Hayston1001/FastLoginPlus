/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminPremiumCommandTest {
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final List<String> feedback = new ArrayList<>();
    private EasyAuth344Bridge bridge;
    private CommandSourceStack source;
    private MinecraftServer server;

    @BeforeEach
    void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bridge = mock(EasyAuth344Bridge.class);
        source = mock(CommandSourceStack.class);
        server = mock(MinecraftServer.class);
        when(source.getServer()).thenReturn(server);
        when(source.getTextName()).thenReturn("Admin");
        when(source.permissions()).thenReturn(LevelBasedPermissionSet.GAMEMASTER);
        doAnswer(call -> {
            Supplier<Component> message = call.getArgument(0);
            feedback.add(message.get().getString());
            return null;
        }).when(source).sendSuccess(any(), eq(false));
        FastLoginFabric.registerCommands(dispatcher, bridge);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void lowPermissionCannotSelectCarrierOrCallBridge(int level) throws Exception {
        when(source.permissions()).thenReturn(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
        assertEquals(0, dispatcher.execute("flp premium OtherUser", source));
        assertEquals(List.of(FabricMessages.text("admin-command-permission").getString()), feedback);
        verifyNoInteractions(bridge, server);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void authorizedPlayerUsesOwnConnectionAndReceivesFeedback(int level) throws Exception {
        when(source.permissions()).thenReturn(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
        ServerPlayer invoker = mock(ServerPlayer.class);
        when(source.getPlayer()).thenReturn(invoker);
        doAnswer(call -> {
            Consumer<Component> result = call.getArgument(3);
            result.accept(Component.literal("requested"));
            return null;
        }).when(bridge).adminPremium(eq(invoker), eq("OtherUser"), eq("Admin"), any());
        assertEquals(1, dispatcher.execute("flp premium OtherUser", source));
        verify(bridge).adminPremium(eq(invoker), eq("OtherUser"), eq("Admin"), any());
        assertEquals(List.of("requested"), feedback);
        verifyNoInteractions(server);
    }

    @Test
    void consoleBorrowsFirstOnlinePlayer() throws Exception {
        when(source.permissions()).thenReturn(LevelBasedPermissionSet.OWNER);
        PlayerList players = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(players);
        ServerPlayer carrier = mock(ServerPlayer.class);
        when(players.getPlayers()).thenReturn(List.of(carrier, mock(ServerPlayer.class)));
        assertEquals(1, dispatcher.execute("flp premium OtherUser", source));
        verify(bridge).adminPremium(eq(carrier), eq("OtherUser"), eq("Admin"), any());
    }

    @Test
    void consoleWithoutOnlineCarrierRefuses() throws Exception {
        when(source.permissions()).thenReturn(LevelBasedPermissionSet.OWNER);
        PlayerList players = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(List.of());
        assertEquals(0, dispatcher.execute("flp premium OtherUser", source));
        assertEquals(List.of(FabricMessages.text("admin-command-no-carrier").getString()), feedback);
        verifyNoInteractions(bridge);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void onlineSuggestionsRequireAdminPermission(int level) throws Exception {
        when(source.permissions()).thenReturn(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
        when(source.getOnlinePlayerNames()).thenReturn(List.of("OtherUser", "Admin"));
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("flp premium O", source)).get();
        assertEquals(level == 2 ? List.of("OtherUser") : List.of(),
                suggestions.getList().stream().map(suggestion -> suggestion.getText()).toList());
        verifyNoInteractions(bridge);
    }

    @Test
    void targetIsASingleWord() {
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("flp premium OtherUser extra", source));
        verifyNoInteractions(bridge);
    }

    @Test
    void noArgumentStillUsesTheExistingSelfBindingPath() throws Exception {
        ServerPlayer player = mock(ServerPlayer.class);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "SelfUser");
        when(source.getPlayerOrException()).thenReturn(player);
        when(player.getGameProfile()).thenReturn(profile);
        IdentityClaim claim = mock(IdentityClaim.class);
        try (var fabric = mockStatic(FastLoginFabric.class, CALLS_REAL_METHODS)) {
            fabric.when(() -> FastLoginFabric.parseClaim(profile)).thenReturn(claim);
            assertEquals(1, dispatcher.execute("flp premium", source));
            verify(bridge).beginBinding(player, claim);
            assertTrue(feedback.isEmpty());
        }
    }
}
