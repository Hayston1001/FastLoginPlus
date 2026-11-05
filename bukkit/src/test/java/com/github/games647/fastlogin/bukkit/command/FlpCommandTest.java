/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.command;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FlpCommandTest {

    private static final String PREFIX = "fastloginplus.bukkit.command.";

    @Test
    void deleteCompletionUsesTheDeletePermissionNode() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(PREFIX + "delete")).thenReturn(true);
        Player alice = onlinePlayer("Alice");
        Player bob = onlinePlayer("Bob");
        FlpCommand command = new FlpCommand(mock(FastLoginBukkit.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Arrays.asList(alice, bob));
            assertEquals(Collections.singletonList("Alice"),
                    command.onTabComplete(sender, mock(Command.class), "flp",
                            new String[]{"delete", "al"}));
            assertEquals(Arrays.asList("Alice", "Bob"),
                    command.onTabComplete(sender, mock(Command.class), "flp",
                            new String[]{"del", ""}));
        }
    }

    @Test
    void deleteWithoutPermissionCompletesNothing() {
        CommandSender sender = mock(CommandSender.class);
        FlpCommand command = new FlpCommand(mock(FastLoginBukkit.class));

        assertTrue(command.onTabComplete(sender, mock(Command.class), "flp",
                new String[]{"delete", ""}).isEmpty());
        assertTrue(command.onTabComplete(sender, mock(Command.class), "flp",
                new String[]{"del", ""}).isEmpty());
    }

    @Test
    void premiumAndCrackedStillRequireTheOtherPermission() {
        Player alice = onlinePlayer("Alice");
        FlpCommand command = new FlpCommand(mock(FastLoginBukkit.class));

        CommandSender other = mock(CommandSender.class);
        when(other.hasPermission(PREFIX + "premium.other")).thenReturn(true);
        CommandSender selfOnly = mock(CommandSender.class);
        when(selfOnly.hasPermission(PREFIX + "premium")).thenReturn(true);
        when(selfOnly.hasPermission(PREFIX + "cracked")).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singletonList(alice));
            assertEquals(Collections.singletonList("Alice"),
                    command.onTabComplete(other, mock(Command.class), "flp",
                            new String[]{"premium", ""}));
            assertTrue(command.onTabComplete(selfOnly, mock(Command.class), "flp",
                    new String[]{"premium", ""}).isEmpty());
            assertTrue(command.onTabComplete(selfOnly, mock(Command.class), "flp",
                    new String[]{"cracked", ""}).isEmpty());
        }
    }

    private static Player onlinePlayer(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
