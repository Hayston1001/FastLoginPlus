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
package com.github.games647.fastlogin.bukkit.listener;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.games647.craftapi.model.skin.Textures;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

public class PaperCacheListener implements Listener {

    private final FastLoginBukkit plugin;

    public PaperCacheListener(final FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    // On Paper, profile.complete(true) is called right after AsyncPlayerPreLoginEvent.
    // If the profile lacks textures at that point, Paper fills them from its filledProfileCache
    // (which may contain a stale skin from a previous session).
    // Setting the skin here ensures the profile already has correct textures before complete(true).
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != Result.ALLOWED) {
            return;
        }

        if (!plugin.getConfig().getBoolean("forwardSkin")) {
            return;
        }

        // event gives us only IP, not the port, so we need to loop through all the sessions
        for (BukkitLoginSession session : plugin.getLoginSessions().values()) {
            if (!event.getName().equals(session.getUsername())) {
                continue;
            }

            // Skip FLP skin if SkinsRestorer has a custom skin — SR skin takes priority.
            // Set an empty placeholder so Paper's hasTextures() returns true and skips
            // filledProfileCache (which may hold a stale skin). SR's SkinProperty.tryParse
            // rejects empty values, so hasOnlineProperties() → false and SR applies its skin.
            if (plugin.getSkinsRestorerCompat().hasCustomSkin(session.getUuid())) {
                plugin.getLog().debug("Skipping FastLogin skin for {} — SkinsRestorer custom skin detected",
                        session.getUsername());
                event.getPlayerProfile().setProperty(new ProfileProperty(Textures.KEY, "", ""));
                break;
            }

            session.getSkin().ifPresent(skin -> event.getPlayerProfile().setProperty(new ProfileProperty(Textures.KEY,
                    skin.getValue(), skin.getSignature())));
            break;
        }
    }

}
