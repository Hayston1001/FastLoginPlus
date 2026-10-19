/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.LoginActionMessage.Type;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.ForceLoginManagement;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginAutoLoginEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

public class ForceLoginTask
        extends ForceLoginManagement<Player, CommandSource, VelocityLoginSession, FastLoginVelocity> {

    private final RegisteredServer server;

    //treat player as if they had a premium account, even when they don't use to do auto login for Floodgate
    private final boolean forcedOnlineMode;

    public ForceLoginTask(FastLoginCore<Player, CommandSource, FastLoginVelocity> core,
                          Player player, RegisteredServer server, VelocityLoginSession session,
                          boolean forcedOnlineMode) {
        super(core, player, session);

        this.server = server;
        this.forcedOnlineMode = forcedOnlineMode;
    }

    public ForceLoginTask(FastLoginCore<Player, CommandSource, FastLoginVelocity> core, Player player,
                          RegisteredServer server, VelocityLoginSession session) {
        this(core, player, server, session, false);
    }

    @Override
    public void run() {
        if (session == null) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("No active login session on force handling for {}", player);
            }
            return;
        }

        super.run();
        if (!isOnlineMode()) {
            session.setAlreadySaved(true);
            if (session.getIdentityDecision()
                    == com.github.games647.fastlogin.protocol.IdentityClaim.Decision.OFFLINE && isOnline(player)) {
                sendDeclaration(Type.CRACKED, session);
            }
        }
    }

    @Override
    public boolean forceLogin(Player player) {
        if (session.isAlreadyLogged()) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Ignoring second force login attempt for {}", player);
            }
            return true;
        }

        session.setAlreadyLogged(true);
        return super.forceLogin(player);
    }

    @Override
    protected void notifySameNameCracked(StoredProfile profile) {
        if (!profile.isSameNameCracked()) {
            core.consumeSameNameCrackedNotice(getName(player));
            return;
        }
        core.getPlugin().getCrackedNoticeDelivery().deliver(player, server);
    }

    @Override
    public FastLoginAutoLoginEvent callFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile) {
        // Unreachable on Velocity: ForceLoginManagement only calls
        // this when an auth plugin hook is set, which never happens on the proxy
        // side. Fire without blocking rather than .get() in case that changes;
        // handler cancellation is not awaited here.
        VelocityFastLoginAutoLoginEvent event = new VelocityFastLoginAutoLoginEvent(session, profile);
        core.getPlugin().getProxy().getEventManager().fire(event);
        return event;
    }

    @Override
    public boolean forceRegister(Player player) {
        return session.isAlreadyLogged() || super.forceRegister(player);
    }

    @Override
    public void onForceActionSuccess(LoginSession session) {
        //sub channel name
        Type type = Type.LOGIN;
        if (session.needsRegistration()) {
            type = Type.REGISTER;
        }
        sendDeclaration(type, session);
    }

    private void sendDeclaration(Type type, LoginSession session) {
        session.computeFacts(core);
        if (type != Type.CRACKED && !session.getFacts().autoLogin()) {
            // The unchanged profile claim still carries identity and policy. Old backends must see no force intent.
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Not sending force {} for {}: proxy auto-login policy is disabled",
                        type, player.getUsername());
            }
            return;
        }
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Sending force {} for {} towards server {}",
                    type, player.getUsername(), server);
        }

        UUID proxyId = core.getPlugin().getProxyId();
        // also hand over the Mojang UUID verified at GameProfileRequestEvent. The
        // backend needs it to stamp AuthMe's premium_uuid when premiumUuid:false, where the
        // forwarded connection UUID is deliberately the offline one and therefore carries no
        // premium signal at all. Null when this connection was not verified as premium.
        // The trailing facts are the proxy's observations plus the recorded upgrade
        // consent; the backend decides the login method from them.
        UUID verified = type == Type.CRACKED ? null : session.getUuid();
        LoginActionMessage loginMessage = new LoginActionMessage(type, player.getUsername(), proxyId, verified);
        loginMessage.setFacts(session.getFacts());
        loginMessage.setDeclaration(type == Type.CRACKED
                ? com.github.games647.fastlogin.protocol.IdentityClaim.Decision.OFFLINE
                : verified == null ? com.github.games647.fastlogin.protocol.IdentityClaim.Decision.UNDECIDED
                : com.github.games647.fastlogin.protocol.IdentityClaim.Decision.PREMIUM_VERIFIED, player.getUniqueId());
        core.getPlugin().sendPluginMessage(server, loginMessage);
    }

    @Override
    public String getName(Player player) {
        return player.getUsername();
    }

    @Override
    public void denyLogin(Player player, String message) {
        player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public boolean isOnline(Player player) {
        return player.isActive();
    }

    @Override
    public boolean isOnlineMode() {
        return forcedOnlineMode || player.isOnlineMode();
    }
}
