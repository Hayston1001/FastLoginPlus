/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.bukkit.command;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Umbrella command for /flp when AuthMe 6.0+ is present.
 * Routes /flp premium, /flp cracked, /flp delete to their handlers.
 */
public class FlpCommand implements CommandExecutor, TabCompleter {

    private final FastLoginBukkit plugin;
    private final PremiumCommand premiumCmd;
    private final CrackedCommand crackedCmd;
    private final DeleteCommand deleteCmd;

    public FlpCommand(FastLoginBukkit plugin) {
        this.plugin = plugin;
        this.premiumCmd = new PremiumCommand(plugin);
        this.crackedCmd = new CrackedCommand(plugin);
        this.deleteCmd = new DeleteCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eFastLoginPlus §7v" + plugin.getDescription().getVersion());
            sender.sendMessage("§7Usage: /flp premium|cracked|delete [player]");
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = args.length > 1
            ? Arrays.copyOfRange(args, 1, args.length)
            : new String[0];

        switch (sub) {
            case "premium":
            case "prem":
                return premiumCmd.onCommand(sender, command, label + " premium", subArgs);
            case "cracked":
            case "unpremium":
                return crackedCmd.onCommand(sender, command, label + " cracked", subArgs);
            case "delete":
            case "del":
                return deleteCmd.onCommand(sender, command, label + " delete", subArgs);
            default:
                sender.sendMessage("§cUnknown subcommand: " + sub);
                sender.sendMessage("§7Usage: /flp premium|cracked|delete [player]");
                return true;
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args.length == 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return Arrays.stream(new String[]{"premium", "cracked", "delete"})
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        // Second argument: online player names
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
