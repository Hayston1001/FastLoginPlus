/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.core.shared.FloodgateState;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class PremiumPlaceholder extends PlaceholderExpansion {

    private static final String PLACEHOLDER_STATUS = "status";
    private static final String PLACEHOLDER_IS_PREMIUM = "is_premium";
    private static final String PLACEHOLDER_FLOODGATE = "floodgate";

    private final FastLoginBukkit plugin;

    public PremiumPlaceholder(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        return Arrays.asList(PLACEHOLDER_STATUS, PLACEHOLDER_IS_PREMIUM, PLACEHOLDER_FLOODGATE);
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null) {
            return null;
        }

        switch (identifier) {
            case PLACEHOLDER_STATUS:
                return plugin.getStatus(player.getUniqueId()).getReadableName();
            case PLACEHOLDER_IS_PREMIUM:
                return String.valueOf(plugin.getStatus(player.getUniqueId()).isPremium());
            case PLACEHOLDER_FLOODGATE:
                FloodgateState floodgate = plugin.getPlayerFloodgateState().get(player.getUniqueId());
                return floodgate != null ? floodgate.getReadableName() : "Java";
            default:
                return null;
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return plugin.getName();
    }

    @Override
    public String getRequiredPlugin() {
        return plugin.getName();
    }

    @Override
    public @NotNull String getAuthor() {
        //noinspection deprecation
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        //noinspection deprecation
        return plugin.getDescription().getVersion();
    }
}
