/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.task;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.event.BungeeFastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.LoginActionMessage.Type;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.ForceLoginManagement;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;

import java.util.UUID;

public class ForceLoginTask
        extends ForceLoginManagement<ProxiedPlayer, CommandSender, BungeeLoginSession, FastLoginBungee> {

    private final Server server;

    //treat player as if they had a premium account, even when they don't
    //use for Floodgate auto login/register
    private final boolean forcedOnlineMode;

    public ForceLoginTask(FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core,
                          ProxiedPlayer player, Server server, BungeeLoginSession session, boolean forcedOnlineMode) {
        super(core, player, session);

        this.server = server;
        this.forcedOnlineMode = forcedOnlineMode;
    }

    public ForceLoginTask(FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core, ProxiedPlayer player,
            Server server, BungeeLoginSession session) {
        this(core, player, server, session, false);
    }

    @Override
    public void run() {
        if (session == null) {
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
    public boolean forceLogin(ProxiedPlayer player) {
        if (session.isAlreadyLogged()) {
            return true;
        }

        session.setAlreadyLogged(true);
        return super.forceLogin(player);
    }

    @Override
    public FastLoginAutoLoginEvent callFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile) {
        return core.getPlugin().getProxy().getPluginManager()
                .callEvent(new BungeeFastLoginAutoLoginEvent(session, profile));
    }

    @Override
    public boolean forceRegister(ProxiedPlayer player) {
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
                        type, player.getName());
            }
            return;
        }
        UUID proxyId = UUID.fromString(ProxyServer.getInstance().getConfig().getUuid());
        // also hand over the Mojang UUID verified at LoginEvent. The backend needs it
        // to stamp AuthMe's premium_uuid when premiumUuid:false, where the forwarded connection
        // UUID is deliberately the offline one and therefore carries no premium signal at all.
        // Null when this connection was not verified as premium.
        // The trailing facts are the proxy's observations plus the recorded upgrade
        // consent; the backend decides the login method from them.
        UUID verified = type == Type.CRACKED ? null : session.getUuid();
        LoginActionMessage loginMessage = new LoginActionMessage(type, player.getName(), proxyId, verified);
        loginMessage.setFacts(session.getFacts());
        loginMessage.setDeclaration(type == Type.CRACKED
                ? com.github.games647.fastlogin.protocol.IdentityClaim.Decision.OFFLINE
                : verified == null ? com.github.games647.fastlogin.protocol.IdentityClaim.Decision.UNDECIDED
                : com.github.games647.fastlogin.protocol.IdentityClaim.Decision.PREMIUM_VERIFIED, player.getUniqueId());

        core.getPlugin().sendPluginMessage(server, loginMessage);
    }

    @Override
    public String getName(ProxiedPlayer player) {
        return player.getName();
    }

    @Override
    public void denyLogin(ProxiedPlayer player, String message) {
        player.disconnect(TextComponent.fromLegacyText(message));
    }

    @Override
    public boolean isOnline(ProxiedPlayer player) {
        return player.isConnected();
    }

    @Override
    public boolean isOnlineMode() {
        return forcedOnlineMode || player.getPendingConnection().isOnlineMode();
    }
}
