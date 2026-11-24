/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.task;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.BungeeLoginSource;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.event.BungeeFastLoginPreLoginEvent;
import com.github.games647.fastlogin.bungee.listener.VerificationCloseWatcher;
import com.github.games647.fastlogin.core.shared.JoinManagement;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PreLoginEvent;

import java.util.concurrent.TimeUnit;

public class AsyncPremiumCheck extends JoinManagement<ProxiedPlayer, CommandSender, BungeeLoginSource>
        implements Runnable {

    /** Must fire before BungeeCord's default 30s login read timeout. */
    private static final long VERIFICATION_DISCONNECT_DELAY_SECONDS = 25L;

    private final FastLoginBungee plugin;
    private final PreLoginEvent preLoginEvent;

    private final String username;
    private final PendingConnection connection;

    public AsyncPremiumCheck(FastLoginBungee plugin, PreLoginEvent preLoginEvent, PendingConnection connection,
                             String username) {
        super(plugin.getCore(), plugin.getCore().getAuthPluginHook(), plugin.getBedrockService());

        this.plugin = plugin;
        this.preLoginEvent = preLoginEvent;
        this.connection = connection;
        this.username = username;
    }

    @Override
    public void run() {
        plugin.getSession().remove(connection);

        try {
            super.onLogin(username, new BungeeLoginSource(connection, preLoginEvent));
        } finally {
            preLoginEvent.completeIntent(plugin);
        }
    }

    @Override
    public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, BungeeLoginSource source,
                                                             StoredProfile profile) {
        return plugin.getProxy().getPluginManager()
                .callEvent(new BungeeFastLoginPreLoginEvent(username, source, profile));
    }

    @Override
    public void requestPremiumLogin(BungeeLoginSource source, StoredProfile profile,
                                    String username, boolean registered) {
        source.enableOnlinemode();
        plugin.getSession().put(source.getConnection(), new BungeeLoginSession(username, registered, profile));

        plugin.getCore().markPendingVerification(username, source.getConnection());
        VerificationCloseWatcher.attach(plugin, username, source.getConnection());
        // BungeeCord's default read timeout would close the login at 30s without a
        // reason; give the player the explicit message a few seconds earlier.
        plugin.getProxy().getScheduler().schedule(plugin, () -> {
            if (plugin.getCore().hasPendingVerification(username, source.getConnection())) {
                plugin.disconnectPendingVerification(username, source.getConnection());
            }
        }, VERIFICATION_DISCONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void startCrackedSession(BungeeLoginSource source, StoredProfile profile, String username) {
        BungeeLoginSession session = new BungeeLoginSession(username, false, profile);
        session.setIdentityDecision(com.github.games647.fastlogin.protocol.IdentityClaim.Decision.OFFLINE);
        plugin.getSession().put(source.getConnection(), session);
    }
}
