/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.shared.JoinManagement;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim.Decision;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.VelocityLoginSource;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginPreLoginEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncPremiumCheck extends JoinManagement<Player, CommandSource, VelocityLoginSource>
        implements Runnable {

    private final FastLoginVelocity plugin;
    private final String username;
    private final PreLoginEvent preLoginEvent;
    private final InboundConnection connection;

    public AsyncPremiumCheck(FastLoginVelocity plugin, InboundConnection connection, String username,
                             PreLoginEvent preLoginEvent) {
        super(plugin.getCore(), plugin.getCore().getAuthPluginHook(), plugin.getBedrockService());
        this.plugin = plugin;
        this.connection = connection;
        this.username = username;
        this.preLoginEvent = preLoginEvent;
    }

    @Override
    public void run() {
        if (!preLoginEvent.getResult().isAllowed()) {
            return;
        }
        plugin.getSession().remove(connection.getRemoteAddress());
        VelocityLoginSource source = new VelocityLoginSource(connection, preLoginEvent);
        super.onLogin(username, source);
    }

    @Override
    public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, VelocityLoginSource source,
                                                             StoredProfile profile) {
        VelocityFastLoginPreLoginEvent event = new VelocityFastLoginPreLoginEvent(username, source, profile);
        try {
            // bounded wait: this runs on a shared scheduler thread
            // — an unbounded .get() can starve the pool; a continuation is not
            // possible mid-flow inside JoinManagement.onLogin
            return plugin.getProxy().getEventManager().fire(event).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupt flag
            throw new IllegalStateException("Interrupted before FLP login decision", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("FLP pre-login event did not complete", e);
        }
    }

    @Override
    public void requestPremiumLogin(VelocityLoginSource source, StoredProfile profile,
                                    String username, boolean registered) {
        VelocityLoginSession session = new VelocityLoginSession(username, registered, profile);
        plugin.getSession().put(source.getConnection().getRemoteAddress(), session);

        plugin.getCore().markPendingVerification(username, source.getConnection(), source.getConnection()::isActive);
        source.enableOnlinemode();
    }

    @Override
    public void startCrackedSession(VelocityLoginSource source, StoredProfile profile, String username) {
        VelocityLoginSession session = new VelocityLoginSession(username, false, profile);
        session.setIdentityDecision(Decision.OFFLINE);
        plugin.getSession().put(source.getConnection().getRemoteAddress(), session);
        source.enableOfflinemode();
    }
}
