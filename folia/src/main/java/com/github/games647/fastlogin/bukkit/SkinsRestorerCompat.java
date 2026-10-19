/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;

import com.github.games647.craftapi.model.skin.SkinProperty;

import net.skinsrestorer.api.SkinsRestorerProvider;

/**
 * Compatibility helper for SkinsRestorer.
 * <p>
 * Checks whether a player has a custom skin set via SkinsRestorer's {@code /skin} command.
 * If so, FastLoginPlus will skip applying its own premium skin to avoid overriding the player's choice.
 */
public final class SkinsRestorerCompat {

    private static final String SR_PLUGIN_NAME = "SkinsRestorer";

    private final FastLoginBukkit plugin;
    private volatile boolean available;

    public SkinsRestorerCompat(FastLoginBukkit plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getServer().getPluginManager().getPlugin(SR_PLUGIN_NAME) != null;
        if (this.available) {
            plugin.getLog().info("SkinsRestorer detected — enabling skin compatibility");
        }
    }

    /**
     * Fetches the custom skin SkinsRestorer has stored for the given player.
     * <p>
     * The result is FastLoginPlus' own skin model, so callers do not have to link against the
     * SkinsRestorer API themselves. Returns {@code null} when the player has no stored skin, the
     * plugin is absent, or its API is not available (for example proxy mode without a shared
     * database).
     *
     * @param uuid the player's UUID (premium UUID preferred)
     * @return the stored skin, or null if there is none to apply
     */
    public SkinProperty getCustomSkin(UUID uuid) {
        if (!available || uuid == null) {
            return null;
        }

        try {
            return SkinsRestorerProvider.get()
                    .getPlayerStorage()
                    .getSkinOfPlayer(uuid)
                    .map(skin -> new SkinProperty(skin.getValue(), skin.getSignature()))
                    .orElse(null);
        } catch (Exception e) {
            // SR API not initialized (e.g. proxy mode without local DB) — safe to ignore
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("SkinsRestorer API check failed for {}: {}", uuid, e.getMessage());
            }
            return null;
        }
    }

    /**
     * Fetches the custom skin SkinsRestorer stored for this login, trying the verified Mojang UUID
     * first and the UUID the connection actually carries second.
     * <p>
     * The two differ whenever FastLoginPlus keeps the premium UUID out of the connection —
     * {@code premiumUuid: false} on a proxy, the setting used by servers that need stable offline
     * UUIDs — or on a direct connection that AuthMe does not force into online mode. SkinsRestorer
     * keys its storage by the UUID the server sees, so looking up the premium UUID alone never finds
     * the skin there, and FastLoginPlus would keep overriding the player's own choice.
     *
     * @param premiumUuid    the verified Mojang UUID, may be null
     * @param connectionUuid the UUID the connection carries, may be null
     * @return the stored skin, or null if SkinsRestorer has none for either UUID
     */
    public SkinProperty getCustomSkin(UUID premiumUuid, UUID connectionUuid) {
        for (UUID candidate : skinCandidateUuids(premiumUuid, connectionUuid)) {
            SkinProperty skin = getCustomSkin(candidate);
            if (skin != null) {
                return skin;
            }
        }

        return null;
    }

    /**
     * @param premiumUuid    the verified Mojang UUID, may be null
     * @param connectionUuid the UUID the connection carries, may be null
     * @return the UUIDs to look up in SkinsRestorer, in order, without duplicates or nulls
     */
    static List<UUID> skinCandidateUuids(UUID premiumUuid, UUID connectionUuid) {
        List<UUID> candidates = new ArrayList<>(2);
        if (premiumUuid != null) {
            candidates.add(premiumUuid);
        }

        if (connectionUuid != null && !connectionUuid.equals(premiumUuid)) {
            candidates.add(connectionUuid);
        }

        return candidates;
    }

    /**
     * @return true if SkinsRestorer plugin is installed on this server
     */
    public boolean isAvailable() {
        return available;
    }
}
