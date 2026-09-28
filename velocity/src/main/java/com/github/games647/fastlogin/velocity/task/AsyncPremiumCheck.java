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
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.shared.JoinManagement;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim.Decision;
import com.github.games647.fastlogin.protocol.AccountStatusWire;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.VelocityLoginSource;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginPreLoginEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;

import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncPremiumCheck extends JoinManagement<Player, CommandSource, VelocityLoginSource>
        implements Runnable {

    private final FastLoginVelocity plugin;
    private final String username;
    private final PreLoginEvent preLoginEvent;
    private final InboundConnection connection;
    private AccountStatusWire.Result accountStatus;

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
        plugin.getSession().remove(connection.getRemoteAddress());
        VelocityLoginSource source = new VelocityLoginSource(connection, preLoginEvent);
        if (plugin.isFabricNativeAuthEnabled()) {
            try {
                accountStatus = plugin.getAccountStatusClient().query(username);
            } catch (IOException | IllegalArgumentException unavailable) {
                plugin.getLog().warn("Could not verify Fabric account ownership for {}: {}",
                        username, unavailable.getMessage());
                source.kick("FastLoginPlus: Fabric account status is unavailable");
                return;
            }
            if (accountStatus.getStatus() == AccountStatusWire.Status.CONFLICT) {
                source.kick("FastLoginPlus: Fabric account ownership conflict");
                return;
            }
            if (accountStatus.getStatus() == AccountStatusWire.Status.BIND_PENDING) {
                StoredProfile profile = plugin.getCore().getStorage().loadProfile(username);
                if (profile != null && profile.isOnlinemodePreferred()) {
                    source.kick("FastLoginPlus: proxy account conflicts with pending bind");
                    return;
                }
                if (profile == null) {
                    profile = new StoredProfile(null, username, false, FloodgateState.FALSE,
                            source.getAddress().getAddress().getHostAddress());
                }
                requestPremiumLogin(source, profile, username, true);
                VelocityLoginSession proof = plugin.getSession().get(connection.getRemoteAddress());
                proof.setBindingProof(true);
                return;
            }
            if (accountStatus.getStatus() == AccountStatusWire.Status.BOUND) {
                StoredProfile profile = plugin.getCore().getStorage().loadProfile(username);
                if (profile != null && profile.isOnlinemodePreferred()
                        && !accountStatus.getUuid().equals(profile.getId())) {
                    source.kick("FastLoginPlus: proxy and Fabric account UUIDs conflict");
                    return;
                }
                if (profile == null) {
                    profile = new StoredProfile(null, username, false, FloodgateState.FALSE,
                            source.getAddress().getAddress().getHostAddress());
                }
                requestPremiumLogin(source, profile, username, true);
                VelocityLoginSession bound = plugin.getSession().get(connection.getRemoteAddress());
                bound.setBackendBoundUuid(accountStatus.getUuid());
                return;
            }
        }
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
            return event;
        } catch (ExecutionException | TimeoutException e) {
            core.getPlugin().getLog().error("Error firing event", e);
            return event;
        }
    }

    @Override
    public void requestPremiumLogin(VelocityLoginSource source, StoredProfile profile,
                                    String username, boolean registered) {
        source.enableOnlinemode();
        VelocityLoginSession session = new VelocityLoginSession(username, registered, profile);
        plugin.getSession().put(source.getConnection().getRemoteAddress(), session);

        String ip = source.getAddress().getAddress().getHostAddress();
        plugin.getCore().addLoginAttempt(ip, username);
    }

    @Override
    public void startCrackedSession(VelocityLoginSource source, StoredProfile profile, String username) {
        VelocityLoginSession session = new VelocityLoginSession(username, false, profile);
        session.setIdentityDecision(Decision.OFFLINE);
        plugin.getSession().put(source.getConnection().getRemoteAddress(), session);
    }

    @Override
    protected void startUndecidedSession(VelocityLoginSource source, StoredProfile profile, String username) {
        VelocityLoginSession session = new VelocityLoginSession(username, false, profile);
        plugin.getSession().put(source.getConnection().getRemoteAddress(), session);
    }

    @Override
    protected boolean shouldReserveUnknownPremiumNames() {
        // A password-owned name remains behind EasyAuth's password lock even
        // when Mojang's name lookup is unavailable. Unclaimed or bound names
        // must keep the unresolved decision closed.
        return plugin.isFabricNativeAuthEnabled() && (accountStatus == null
                || accountStatus.getStatus() != AccountStatusWire.Status.PASSWORD);
    }

    @Override
    protected boolean canUsePremiumAccount(String playerName, UUID mojangUuid) {
        if (!plugin.isFabricNativeAuthEnabled()) {
            return true;
        }
        return accountStatus != null && (accountStatus.getStatus() == AccountStatusWire.Status.OPEN
                || (accountStatus.getStatus() == AccountStatusWire.Status.BOUND
                && mojangUuid != null && mojangUuid.equals(accountStatus.getUuid())));
    }

    @Override
    protected boolean isPreRegisteredPremiumAccount(String playerName, UUID mojangUuid) {
        return plugin.isFabricNativeAuthEnabled() && accountStatus != null
                && accountStatus.getStatus() == AccountStatusWire.Status.BOUND
                && mojangUuid != null && mojangUuid.equals(accountStatus.getUuid());
    }
}
