/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator;
import com.github.games647.fastlogin.bukkit.compat.AuthMeVersionDetector;
import com.github.games647.fastlogin.bukkit.listener.AuthMeCommandGuard;
import com.github.games647.fastlogin.bukkit.listener.AuthMeTakeoverListener;
import com.github.games647.fastlogin.core.shared.LoginMethodDecider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.net.InetSocketAddress;
import java.util.UUID;

/** Account mutations and listeners owned exclusively by the selected AuthMe adapter. */
final class AuthMeIntegration {

    final AuthMeVersionDetector version;
    final AuthMePremiumIntegrator accounts;
    private final FastLoginBukkit plugin;
    private boolean takeover;
    private boolean warnedPin;

    AuthMeIntegration(FastLoginBukkit plugin, AuthMeVersionDetector version, AuthMePremiumIntegrator accounts) {
        this.plugin = plugin;
        this.version = version;
        this.accounts = accounts;
    }

    void start() {
        if (!version.isAuthMe6()) {
            plugin.getLog().info("AuthMe {} selected: using standard account handling", version.getVersion());
            return;
        }
        boolean proxy = plugin.getBungeeManager().isEnabled();
        PluginManager manager = plugin.getServer().getPluginManager();
        boolean packetEvents = manager.isPluginEnabled("packetevents");
        takeover = accounts.enforceFlpPremiumControl(proxy);
        pinProxy();
        if (!takeover) {
            plugin.getLog().warn("Failed to fully enforce premium control in AuthMe {}", version.getVersion());
        } else if (shouldWarnAboutCommandLayer(takeover, packetEvents, proxy)) {
            plugin.getLog().warn("PacketEvents is absent. AuthMe's own premium verification is off; "
                    + "FastLoginPlus handles verification and redirects /premium and /freemium to /flp.");
        } else if (!proxy) {
            plugin.getLog().info("Took over AuthMe's premium packet verification");
        }
        manager.registerEvents(new AuthMeCommandGuard(plugin), plugin);
        manager.registerEvents(new AuthMeTakeoverListener(plugin), plugin);
    }

    static boolean shouldWarnAboutCommandLayer(boolean active, boolean packetEvents, boolean proxy) {
        return active && !packetEvents && !proxy;
    }

    boolean ownsCommands() {
        return takeover;
    }

    private void pinProxy() {
        if (!accounts.pinProxyAutoLoginOff() && !warnedPin) {
            warnedPin = true;
            plugin.getLog().warn("AuthMe proxy auto-login pin was not applied; upstream proxy SSO may remain enabled.");
        }
    }

    void reassert(String reason) {
        // Run after AuthMe's reload/packet-library handler, on the host's lifecycle thread.
        plugin.nextGlobalTick(() -> {
            if (!plugin.isServerFullyStarted()) {
                return;
            }
            pinProxy();
            if (plugin.getBungeeManager().isEnabled()) {
                if (accounts.hasProxyAutoLoginPolicy()) {
                    accounts.reassertProxyAutoLogin();
                }
            } else if (plugin.getServer().getPluginManager().isPluginEnabled("packetevents")) {
                plugin.getLog().info("Detected {}; reasserting AuthMe premium control", reason);
                accounts.unregisterPremiumPacketListener();
            }
        });
    }

    void prepareStandalone(String name, BukkitLoginSession session) {
        if (!accounts.isAuthMePremiumEnabled() || !plugin.getConfig().getBoolean("autoLogin")
                || !session.isVerifiedPremium()) {
            return;
        }
        // A verified identity alone does not authorize conversion of a local password account.
        LoginMethodDecider.Outcome decision;
        try {
            decision = LoginMethodDecider.decide(true, accounts.inspectAccount(name),
                    LoginMethodDecider.standaloneConsent(session),
                    true, plugin.getConfig().getBoolean("auto-register-unknown"));
        } catch (Exception unavailable) {
            plugin.getLog().warn("Could not inspect {} before authentication; keeping the password lock",
                    name, unavailable);
            return;
        }
        if (decision != LoginMethodDecider.Outcome.AUTO_LOGIN
                && decision != LoginMethodDecider.Outcome.AUTO_REGISTER_LOGIN) {
            return;
        }
        UUID uuid = session.getUuid();
        accounts.unregisterPremiumPacketListener();
        accounts.injectPendingPremium(name, uuid);
        accounts.injectVerifiedUuid(name, uuid);
        if (accounts.markPlayerAsPremium(name, uuid)) {
            session.setRegistered(true);
        }
    }

    void prepareConfigure(String name, UUID premiumUuid, UUID connectionUuid, Object connection) {
        if (accounts.isAuthMePremiumEnabled()) {
            accounts.injectVerifiedUuid(name, premiumUuid);
            accounts.markPlayerAsPremium(name, premiumUuid);
            closeDialogs(connectionUuid, connection);
        }
    }

    boolean markAfterLogin(Player player, UUID verifiedUuid) {
        if (!accounts.isAuthMePremiumEnabled()) {
            return false;
        }
        UUID uuid = AuthMePremiumIntegrator.resolvePremiumUuid(verifiedUuid, player.getUniqueId());
        return uuid != null && accounts.markPlayerAsPremium(player.getName(), uuid);
    }

    void handlePasswordPolicy(String name, UUID owner, UUID connectionUuid, Object connection,
                              InetSocketAddress address, boolean notify) {
        AuthMePremiumIntegrator.ReleaseOutcome result = accounts.releaseUnusablePlaceholder(name, owner);
        switch (result == null ? AuthMePremiumIntegrator.ReleaseOutcome.FAILED : result) {
            case RELEASED:
            case ALREADY_ABSENT:
            case UUID_MISMATCH:
                plugin.clearPasswordNotice(address, name);
                closeDialogs(connectionUuid, connection);
                if (result == AuthMePremiumIntegrator.ReleaseOutcome.UUID_MISMATCH) {
                    String reason = plugin.getCore().getMessage("ownership-conflict");
                    if (connection != null) {
                        plugin.disconnectConfiguration(name, connection, reason);
                    } else {
                        plugin.denyLogin(name, reason);
                    }
                }
                break;
            default:
                if (notify) {
                    plugin.queuePasswordNotice(address, name, "premium-policy-disabled");
                }
        }
    }

    private void closeDialogs(UUID connectionUuid, Object connection) {
        accounts.closePreJoinRegisterDialog(connectionUuid, connection);
        accounts.closePreJoinLoginDialog(connectionUuid, connection);
    }
}
