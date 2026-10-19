/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.event;

import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.shared.event.FastLoginAntiBotEvent;

import net.md_5.bungee.api.plugin.Cancellable;
import net.md_5.bungee.api.plugin.Event;

import java.net.InetSocketAddress;

public class BungeeFastLoginAntiBotEvent extends Event implements FastLoginAntiBotEvent, Cancellable {

    private final InetSocketAddress address;
    private final String username;
    private final Action action;
    private boolean cancelled;

    public BungeeFastLoginAntiBotEvent(InetSocketAddress address, String username, Action action) {
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
}
