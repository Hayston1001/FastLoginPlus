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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Folia-adapted ForceLoginTask.
 *
 * Key difference: Uses Entity.getScheduler() + CompletableFuture instead of
 * Bukkit.getScheduler().callSyncMethod().
 */
public class ForceLoginTask extends ForceLoginManagement<Player, CommandSender, BukkitLoginSession, FastLoginBukkit> {

    public ForceLoginTask(FastLoginCore<Player, CommandSender, FastLoginBukkit> core, Player player,
                          BukkitLoginSession session) {
        super(core, player, session);
    }

    @Override
    public void run() {
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
        player.getScheduler().run(core.getPlugin(), task -> player.kickPlayer(message), null);
    }

    @Override
    protected void notifyPasswordLogin() {
        player.getScheduler().run(core.getPlugin(), task -> core.getPlugin().sendPasswordNotice(player), null);
    }

    @Override
    protected void notifyPremiumPolicyDisabled() {
        player.getScheduler().run(core.getPlugin(), task -> core.getPlugin().sendPremiumPolicyNotice(player), null);
    }

    @Override
    public boolean isOnline(Player player) {
        // Folia: use Entity.getScheduler() + CompletableFuture instead of callSyncMethod
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        player.getScheduler().run(core.getPlugin(), task -> {
            future.complete(player.isOnline());
        }, null); // null = retired callback (entity removed before task runs)

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            core.getPlugin().getLog().error("Interrupted while checking online status for {}", player, ex);
            return false;
        } catch (ExecutionException | TimeoutException ex) {
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
