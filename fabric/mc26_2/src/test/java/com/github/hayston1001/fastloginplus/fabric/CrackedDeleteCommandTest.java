/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CrackedDeleteCommandTest {
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final List<String> feedback = new ArrayList<>();
    private EasyAuth344Bridge bridge;
    private CommandSourceStack source;
    private MinecraftServer server;

    @BeforeEach
    void setup() {
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
    @CsvSource({"cracked,0", "cracked,1", "delete,0", "delete,1"})
    void permissionCheckedBeforeSelectingCarrier(String command, int level) throws Exception {
        level(level);
        assertEquals(0, dispatcher.execute("flp " + command + " TargetUser", source));
        assertEquals(List.of(FabricMessages.text("admin-command-permission").getString()), feedback);
        verifyNoInteractions(bridge, server);
    }

    @ParameterizedTest
    @CsvSource({"cracked,2", "cracked,3", "cracked,4", "delete,2", "delete,3", "delete,4"})
    void authorizedPlayerRelaysOverOwnConnection(String command, int level) throws Exception {
        level(level);
        ServerPlayer player = mock(ServerPlayer.class);
        when(source.getPlayer()).thenReturn(player);
        assertEquals(1, dispatcher.execute("flp " + command + " TargetUser", source));
        verifyBridge(command, player);
        verifyNoInteractions(server);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cracked", "delete"})
    void consoleBorrowsFirstOnlinePlayer(String command) throws Exception {
        level(4);
        PlayerList list = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(list);
        ServerPlayer carrier = mock(ServerPlayer.class);
        when(list.getPlayers()).thenReturn(List.of(carrier, mock(ServerPlayer.class)));
        assertEquals(1, dispatcher.execute("flp " + command + " TargetUser", source));
        verifyBridge(command, carrier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cracked", "delete"})
    void consoleCannotQueueWithoutAnOnlineCarrier(String command) throws Exception {
        PlayerList list = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(list);
        when(list.getPlayers()).thenReturn(List.of());
        assertEquals(0, dispatcher.execute("flp " + command + " TargetUser", source));
        assertEquals(List.of(FabricMessages.text("admin-premium-no-carrier").getString()), feedback);
        verifyNoInteractions(bridge);
    }

    @ParameterizedTest
    @CsvSource({"cracked,1", "cracked,2", "delete,1", "delete,2"})
    void suggestionsRespectAdminPermission(String command, int level) throws Exception {
        level(level);
        when(source.getOnlinePlayerNames()).thenReturn(List.of("TargetUser"));
        var result = dispatcher.getCompletionSuggestions(dispatcher.parse("flp " + command + " T", source)).get();
        assertEquals(level == 2 ? List.of("TargetUser") : List.of(),
                result.getList().stream().map(suggestion -> suggestion.getText()).toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void selfCrackedChecksPermissionBeforeObtainingPlayer(int level) throws Exception {
        level(level);
        assertEquals(0, dispatcher.execute("flp cracked", source));
        assertEquals(List.of(FabricMessages.text("admin-command-permission").getString()), feedback);
        verify(source, never()).getPlayerOrException();
        verifyNoInteractions(bridge, server);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void authorizedSelfCrackedUsesOnlyOwnConnection(int level) throws Exception {
        level(level);
        ServerPlayer player = mock(ServerPlayer.class);
        when(source.getPlayerOrException()).thenReturn(player);
        assertEquals(1, dispatcher.execute("flp cracked", source));
        verify(bridge).cracked(player);
        verifyNoInteractions(server);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "delete TargetUser extra", "cracked TargetUser extra"})
    void deleteNeedsTargetAndBothTargetsAreSingleWords(String arguments) {
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("flp " + arguments, source));
        verifyNoInteractions(bridge);
    }

    private void level(int value) {
        when(source.permissions()).thenReturn(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(value)));
    }

    private void verifyBridge(String command, ServerPlayer carrier) {
        if ("delete".equals(command)) {
            verify(bridge).adminDelete(eq(carrier), eq("TargetUser"), eq("Admin"), any());
        } else {
            verify(bridge).adminCracked(eq(carrier), eq("TargetUser"), eq("Admin"), any());
        }
    }
}
