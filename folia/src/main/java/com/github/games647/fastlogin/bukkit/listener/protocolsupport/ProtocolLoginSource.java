/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocolsupport;

import com.github.games647.fastlogin.core.shared.LoginSource;
import protocolsupport.api.events.PlayerLoginStartEvent;

import java.net.InetSocketAddress;

public class ProtocolLoginSource implements LoginSource {

    private final PlayerLoginStartEvent loginStartEvent;

    public ProtocolLoginSource(PlayerLoginStartEvent loginStartEvent) {
        this.loginStartEvent = loginStartEvent;
    }

    @Override
    public void enableOnlinemode() {
        loginStartEvent.setOnlineMode(true);
    }

    @Override
    public void kick(String message) {
        loginStartEvent.denyLogin(message);
    }

    @Override
    public InetSocketAddress getAddress() {
        return loginStartEvent.getConnection().getRawAddress();
    }

    public PlayerLoginStartEvent getLoginStartEvent() {
        return loginStartEvent;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "loginStartEvent=" + loginStartEvent
            + '}';
    }
}
