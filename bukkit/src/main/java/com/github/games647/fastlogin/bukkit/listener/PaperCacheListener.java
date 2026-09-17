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
import com.github.games647.craftapi.model.skin.SkinProperty;
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
        // 0.7.0/F19: remember this login's attestation before Paper's filledProfileCache can hand
        // the configuration phase a stale premium profile from an earlier session.
        plugin.recordPreLoginAttestation(event.getName(), event.getPlayerProfile());

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
            // Hand Paper SkinsRestorer's own skin instead of an empty placeholder: SR decides
            // "has online properties" by the property SET being non-empty, and its own pre-login
            // handler (default priority) runs before this one, so writing "" here destroyed the
            // skin that handler had just applied and players rendered with the default skin.
            // Writing the real property keeps the filledProfileCache guard working without
            // depending on either handler's execution order.
            if (plugin.getSkinsRestorerCompat().hasCustomSkin(session.getUuid())) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Skipping FastLogin skin for {} — SkinsRestorer custom skin detected",
                    session.getUsername());
                }
                ProfileProperty customSkin = skinPropertyFor(
                        plugin.getSkinsRestorerCompat().getCustomSkin(session.getUuid()));
                if (customSkin != null) {
                    event.getPlayerProfile().setProperty(customSkin);
                }
                break;
            }

            session.getSkin().ifPresent(skin -> event.getPlayerProfile().setProperty(new ProfileProperty(Textures.KEY,
                    skin.getValue(), skin.getSignature())));
            break;
        }
    }

    /**
     * Builds the profile property for a skin SkinsRestorer owns.
     * <p>
     * Returns {@code null} when there is nothing usable to write — either SkinsRestorer answered
     * with no skin, or the stored value is empty. An empty {@code textures} value must never reach
     * the profile: it counts as "online properties present" for SkinsRestorer and, because this
     * listener runs at {@link EventPriority#HIGHEST}, it would overwrite whatever that plugin
     * applied from its own pre-login handler.
     *
     * @param customSkin the skin SkinsRestorer stored for this player, may be null
     * @return the property to set, or null to leave the profile untouched
     */
    static ProfileProperty skinPropertyFor(SkinProperty customSkin) {
        if (customSkin == null || customSkin.getValue() == null || customSkin.getValue().isEmpty()) {
            return null;
        }

        return new ProfileProperty(Textures.KEY, customSkin.getValue(), customSkin.getSignature());
    }

}
