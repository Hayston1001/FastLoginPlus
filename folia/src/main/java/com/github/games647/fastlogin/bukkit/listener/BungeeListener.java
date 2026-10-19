/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.task.ForceLoginTask;
import com.github.games647.fastlogin.core.PremiumStatus;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.LoginActionMessage.Type;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Folia-adapted BungeeListener.
 *
 * Uses Bukkit.getAsyncScheduler() instead of Bukkit.getScheduler().runTaskAsynchronously().
 */
public class BungeeListener implements PluginMessageListener {

    private final FastLoginBukkit plugin;

    public BungeeListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, Player player, byte[] message) {
        ByteArrayDataInput dataInput = ByteStreams.newDataInput(message);

        LoginActionMessage loginMessage = new LoginActionMessage();
        try {
            loginMessage.readFrom(dataInput);
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            plugin.getLog().warn("Ignoring malformed identity declaration", malformed);
            return;
        }

        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Received plugin message {}", loginMessage);
        }

        Player targetPlayer = player;
        if (!loginMessage.getPlayerName().equals(player.getName())) {
            targetPlayer = Bukkit.getPlayerExact(loginMessage.getPlayerName());
        }

        if (targetPlayer == null) {
            plugin.getLog().warn("Force action player {} not found", loginMessage.getPlayerName());
            return;
        }
        if (loginMessage.getForwardedUuid() != null
                && !loginMessage.getForwardedUuid().equals(targetPlayer.getUniqueId())) {
            plugin.getLog().warn("Ignoring identity declaration for {}: forwarded connection UUID mismatch",
                    loginMessage.getPlayerName());
            return;
        }

        UUID sourceId = loginMessage.getProxyId();
        if (!plugin.getBungeeManager().isProxyAllowed(sourceId)) {
            plugin.getLog().warn("Received proxy id: {} that doesn't exist in the proxy file", sourceId);
            return;
        }
        // If the earlier live-profile claim was unavailable, an already authenticated carrier
        // can deliver the first policy fact. That late fallback only protects later connections.
        plugin.synchronizeAuthMePremiumPolicy(loginMessage.getFacts());
        if (loginMessage.getType() != Type.CRACKED) {
            if (loginMessage.getFacts().policyDeclared() && !loginMessage.isAutoLogin()) {
                plugin.getLog().warn("Proxy force {} for {} contradicts its disabled auto-login policy; "
                        + "applying declared policy instead", loginMessage.getType(), loginMessage.getPlayerName());
            } else if (!loginMessage.getFacts().policyDeclared()
                    && plugin.getCore().getConfig().getBoolean("require-declared-proxy-policy")) {
                plugin.getLog().warn("Ignoring proxy force {} for {}: policy facts are missing and "
                        + "require-declared-proxy-policy is enabled; keeping the auth plugin's own login flow",
                        loginMessage.getType(), loginMessage.getPlayerName());
                return;
            }
        }

        // A player who is already authenticated is the normal case here, not a fault: on a Paper
        // backend the configure phase logs premium players in before the proxy's own message can
        // arrive, so that message is a redundant duplicate whose payload is ignored on purpose.
        // The metadata itself stays — ForceLoginTask sets it as the proxy-ID brute-force guard — but
        // this line belongs in debug: as a warning it read like a failure on every premium login and
        // drowned the messages that do matter (untrusted proxy id, missing player).
        if (targetPlayer.hasMetadata(plugin.getName())) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Ignoring duplicate proxy action {} for already authenticated {}",
                        loginMessage, targetPlayer);
            }
        } else {
            readMessage(targetPlayer, loginMessage);
        }
    }

    private void readMessage(Player player, LoginActionMessage message) {
        String playerName = message.getPlayerName();
        Type type = message.getType();

        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Player info {} command for {} from proxy", type, playerName);
        }
        if (type != Type.CRACKED && message.getFacts().policyDeclared() && !message.isAutoLogin()) {
            // REGISTER must also reach the policy decision for existing password accounts.
            onLoginMessage(player, playerName, message);
        } else if (type == Type.LOGIN) {
            onLoginMessage(player, playerName, message);
        } else if (type == Type.REGISTER) {
            onRegisterMessage(player, playerName, message);
        } else if (type == Type.CRACKED) {
            plugin.getPremiumPlayers().put(player.getUniqueId(), PremiumStatus.CRACKED);
            BukkitLoginSession session = new BukkitLoginSession(playerName, false);
            session.setFacts(message.getFacts());
            session.setIdentityDecision(com.github.games647.fastlogin.protocol.IdentityClaim.Decision.OFFLINE);
            session.setVerifiedPremium(false);
            startLoginTaskIfReady(player, session);
        }
    }

    private void onLoginMessage(Player player, String playerName, LoginActionMessage message) {
        BukkitLoginSession playerSession = new BukkitLoginSession(playerName, true);
        // adopt the Mojang UUID the proxy verified. Without it the session carries no
        // UUID at all, and with premiumUuid:false the fallback in ForceLoginTask (the connection
        // UUID) is the offline one — so AuthMe would never get a premium_uuid written and the
        // player would be asked for a password on every single login. Null stays null: the proxy
        // only omits it when it verified nothing, and resolvePremiumUuid rejects non-v4 values.
        playerSession.setUuid(message.getVerifiedPremiumUuid());
        playerSession.setFacts(message.getFacts());
        playerSession.setVerifiedPremium(true);
        startLoginTaskIfReady(player, playerSession);
    }

    private void onRegisterMessage(Player player, String playerName, LoginActionMessage message) {
        // Folia: use AsyncScheduler instead of Bukkit.getScheduler().runTaskAsynchronously()
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            AuthPlugin<Player> authPlugin = plugin.getCore().getAuthPluginHook();
            try {
                //we need to check if the player is registered on Bukkit too
                boolean registered = authPlugin != null && authPlugin.isRegistered(playerName);
                // Recovery of an auth-plugin premium account also needs a task when the proxy has no account yet:
                // the confirmed login acknowledges premium ownership back to it.
                if (!registered || authPlugin.inspectAccount(playerName)
                        == com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState.PREMIUM_OWNED) {
                    BukkitLoginSession playerSession = new BukkitLoginSession(playerName, registered);
                    playerSession.setUuid(message.getVerifiedPremiumUuid());
                    playerSession.setFacts(message.getFacts());
                    playerSession.setVerifiedPremium(true);
                    startLoginTaskIfReady(player, playerSession);
                }
            } catch (Exception ex) {
                plugin.getLog().error("Failed to query isRegistered for player: {}", player, ex);
            }
        });
    }

    private void startLoginTaskIfReady(Player player, BukkitLoginSession session) {
        plugin.putSession(player.spigot().getRawAddress(), session);

        // only start a new login task if the join event fired earlier. This event then didn't
        boolean result = plugin.getBungeeManager().didJoinEventFired(player);
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info("Delaying force login until join event fired?: {}", result);
        }
        if (result) {
            Runnable forceLoginTask = new ForceLoginTask(plugin.getCore(), player, session);
            // Folia: use AsyncScheduler
            Bukkit.getAsyncScheduler().runNow(plugin, task -> forceLoginTask.run());
        }
    }
}
