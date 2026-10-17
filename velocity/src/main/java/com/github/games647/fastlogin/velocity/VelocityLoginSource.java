/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity;

import com.github.games647.fastlogin.core.shared.LoginSource;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.InetSocketAddress;

public class VelocityLoginSource implements LoginSource {

    private final InboundConnection connection;
    private final PreLoginEvent preLoginEvent;

    public VelocityLoginSource(InboundConnection connection, PreLoginEvent preLoginEvent) {
        this.connection = connection;
        this.preLoginEvent = preLoginEvent;
    }

    @Override
    public void enableOnlinemode() {
        if (preLoginEvent.getResult().isAllowed()) {
            preLoginEvent.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        }
    }

    /** Applies only an explicit FLP offline decision; never replaces a denial. */
    public void enableOfflinemode() {
        if (preLoginEvent.getResult().isAllowed()) {
            preLoginEvent.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        }
    }

    @Override
    public void kick(String message) {
        if (message == null) {
            preLoginEvent.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("Kicked").color(NamedTextColor.WHITE))
            );
        } else {
            preLoginEvent.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(message))
            );
        }
    }

    @Override
    public InetSocketAddress getAddress() {
        return connection.getRemoteAddress();
    }

    public InboundConnection getConnection() {
        return connection;
    }
}
