/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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

    private static final String PERM_PREFIX = "fastloginplus.folia.command.";

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
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("FlpCommand invoked by {} ({}) label={} args={}",
                    sender.getName(), sender.getClass().getSimpleName(), label,
                    Arrays.toString(args));
        }
        if (args.length == 0) {
            if (!sender.isOp()) {
                plugin.getCore().sendLocaleMessage("no-permission", sender);
                return true;
            }
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
                    .filter(s -> sender.hasPermission(PERM_PREFIX + s))
                    .collect(Collectors.toList());
        }

        // Second argument: online player names (only for senders with .other permission)
        if (args.length == 2 && sender.hasPermission(PERM_PREFIX + args[0].toLowerCase() + ".other")) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
