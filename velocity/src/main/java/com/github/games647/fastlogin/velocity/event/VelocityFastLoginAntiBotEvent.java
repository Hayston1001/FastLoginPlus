/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.event;

import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.shared.event.FastLoginAntiBotEvent;

import com.velocitypowered.api.event.ResultedEvent;

import java.net.InetSocketAddress;
import java.util.Objects;

public class VelocityFastLoginAntiBotEvent
    implements FastLoginAntiBotEvent, ResultedEvent<ResultedEvent.GenericResult> {

    private final InetSocketAddress address;
    private final String username;
    private final Action action;
    private boolean cancelled;

    public VelocityFastLoginAntiBotEvent(InetSocketAddress address, String username, Action action) {
        this.address = address;
        this.username = username;
        this.action = action;
    }

    @Override
    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Action getAction() {
        return action;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public GenericResult getResult() {
        return cancelled ? GenericResult.denied() : GenericResult.allowed();
    }

    @Override
    public void setResult(GenericResult result) {
        cancelled = Objects.requireNonNull(result) != GenericResult.allowed();
    }
}
