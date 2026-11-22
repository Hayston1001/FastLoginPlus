/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.task;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.PremiumStatus;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.shared.ForceLoginManagement;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.ExecutionException;

public class ForceLoginTask extends ForceLoginManagement<Player, CommandSender, BukkitLoginSession, FastLoginBukkit> {

    public ForceLoginTask(FastLoginCore<Player, CommandSender, FastLoginBukkit> core, Player player,
                          BukkitLoginSession session) {
        super(core, player, session);
    }

    @Override
    public void run() {
        // block this target player for BungeeCord ID brute force attacks
        FastLoginBukkit plugin = core.getPlugin();
        if (!plugin.isServerFullyStarted() || plugin.getProxyBedrockPreLogin() != null
                && plugin.getProxyBedrockPreLogin().isHandled(player)) {
            return;
        }
        player.setMetadata(core.getPlugin().getName(), new FixedMetadataValue(plugin, true));

        if (session != null && !session.getUsername().equals(player.getName())) {
            String playerName = player.getName();
            plugin.getLog().warn("Player username {} is not matching session {}", playerName, session.getUsername());
            return;
        }

        if (core.getAuthPluginHook() == null) {
            return;
        }
        super.run();
        if (session == null || !player.isOnline()) {
            return;
        }

        PremiumStatus status = PremiumStatus.CRACKED;
        if (isOnlineMode()) {
            status = PremiumStatus.PREMIUM;
        }

        plugin.getPremiumPlayers().put(player.getUniqueId(), status);

        StoredProfile profile = session.getProfile();
        if (profile != null) {
            FloodgateState floodgate = profile.getFloodgate();
            if (floodgate != null) {
                plugin.getPlayerFloodgateState().put(player.getUniqueId(), floodgate);
            }
        }
    }

    @Override
    public FastLoginAutoLoginEvent callFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile) {
        BukkitFastLoginAutoLoginEvent event = new BukkitFastLoginAutoLoginEvent(session, profile);
        core.getPlugin().getServer().getPluginManager().callEvent(event);
        return event;
    }

    @Override
    public void onForceActionSuccess(LoginSession session) {
        if (this.session.isVerifiedPremium() && core.getPlugin().markConfirmedAccount(player, session.getUuid())) {
            forceLogin(player);
        }
        if (core.getPlugin().getBungeeManager().isEnabled()) {
            core.getPlugin().getBungeeManager().sendPluginMessage(player, new SuccessMessage());
        }
    }

    @Override
    public String getName(Player player) {
        return player.getName();
    }

    @Override
    public void denyLogin(Player player, String message) {
        Bukkit.getScheduler().runTask(core.getPlugin(), () -> player.kickPlayer(message));
    }

    @Override
    protected void notifyPasswordLogin() {
        Bukkit.getScheduler().runTask(core.getPlugin(), () -> core.getPlugin().sendPasswordNotice(player));
    }

    @Override
    protected void notifyPremiumPolicyDisabled() {
        Bukkit.getScheduler().runTask(core.getPlugin(), () -> core.getPlugin().sendPremiumPolicyNotice(player));
    }

    @Override
    public boolean isOnline(Player player) {
        try {
            //the player-list isn't thread-safe
            return Bukkit.getScheduler().callSyncMethod(core.getPlugin(), player::isOnline).get();
        } catch (InterruptedException | ExecutionException ex) {
            core.getPlugin().getLog().error("Failed to perform thread-safe online check for {}", player, ex);
            return false;
        }
    }

    @Override
    public boolean isOnlineMode() {
        return session.isVerifiedPremium();
    }

    @Override
    protected boolean usesStandaloneJavaDecision() {
        // A standalone Floodgate compatibility session may set the legacy
        // premium flag, but does not carry a verified Mojang UUID.
        return !core.getPlugin().getBungeeManager().isEnabled() && session.getUuid() != null;
    }
}
