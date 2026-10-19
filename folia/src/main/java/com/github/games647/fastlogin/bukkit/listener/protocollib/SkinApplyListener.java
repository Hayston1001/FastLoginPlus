/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.github.games647.craftapi.model.skin.Textures;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

public class SkinApplyListener implements Listener {

    private final FastLoginBukkit plugin;

    public SkinApplyListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    //run this on the loginEvent to let skins plugins see the skin like in normal Minecraft behaviour
    public void onPlayerLogin(PlayerLoginEvent loginEvent) {
        if (loginEvent.getResult() != Result.ALLOWED) {
            return;
        }

        Player player = loginEvent.getPlayer();

        //go through every session, because player.getAddress is null
        //loginEvent.getAddress is just a InetAddress not InetSocketAddress, so not unique enough
        for (BukkitLoginSession session : plugin.getLoginSessions().values()) {
            if (session.getUsername().equals(player.getName())) {
                // Skip if SkinsRestorer has a custom skin for this player — SR skin takes priority.
                // Both UUIDs this login can have are checked: the verified Mojang one and the one the
                // connection actually carries (they differ under premiumUuid:false, and SkinsRestorer
                // is keyed by the latter).
                if (plugin.getSkinsRestorerCompat()
                        .getCustomSkin(session.getUuid(), player.getUniqueId()) != null) {
                    if (plugin.getCore().isDebug()) {
                        plugin.getLog().info("Skipping FastLogin skin for {} — SkinsRestorer custom skin detected",
                        session.getUsername());
                    }
                    break;
                }

                session.getSkin().ifPresent(skin -> applySkin(player, skin.getValue(), skin.getSignature()));
                break;
            }
        }
    }

    private void applySkin(Player player, String skinData, String signature) {
        WrappedGameProfile gameProfile = WrappedGameProfile.fromPlayer(player);

        WrappedSignedProperty skin = WrappedSignedProperty.fromValues(Textures.KEY, skinData, signature);
        gameProfile.getProperties().put(Textures.KEY, skin);
    }
}
