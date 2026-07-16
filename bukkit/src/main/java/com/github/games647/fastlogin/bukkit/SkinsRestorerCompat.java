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
package com.github.games647.fastlogin.bukkit;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.slf4j.Logger;

import net.skinsrestorer.api.SkinsRestorerProvider;

/**
 * Compatibility helper for SkinsRestorer.
 * <p>
 * Checks whether a player has a custom skin set via SkinsRestorer's {@code /skin} command.
 * If so, FastLoginPlus will skip applying its own premium skin to avoid overriding the player's choice.
 */
public final class SkinsRestorerCompat {

    private static final String SR_PLUGIN_NAME = "SkinsRestorer";

    private final Logger logger;
    private volatile boolean available;

    public SkinsRestorerCompat(Logger logger) {
        this.logger = logger;
        this.available = Bukkit.getServer().getPluginManager().getPlugin(SR_PLUGIN_NAME) != null;
        if (this.available) {
            logger.info("SkinsRestorer detected — enabling skin compatibility");
        }
    }

    /**
     * Checks whether the given player has a custom skin explicitly set via SkinsRestorer.
     * <p>
     * Returns {@code true} if the player used {@code /skin set} and SR has a stored skin identifier
     * for them. In that case, FastLoginPlus should NOT override the skin — SR's skin takes priority.
     *
     * @param uuid the player's UUID (premium UUID preferred)
     * @return true if SR has a custom skin for this player, false otherwise
     */
    public boolean hasCustomSkin(UUID uuid) {
        if (!available || uuid == null) {
            return false;
        }

        try {
            return SkinsRestorerProvider.get()
                    .getPlayerStorage()
                    .getSkinIdOfPlayer(uuid)
                    .isPresent();
        } catch (Exception e) {
            // SR API not initialized (e.g. proxy mode without local DB) — safe to ignore
            logger.debug("SkinsRestorer API check failed for {}: {}", uuid, e.getMessage());
            return false;
        }
    }

    /**
     * @return true if SkinsRestorer plugin is installed on this server
     */
    public boolean isAvailable() {
        return available;
    }
}
