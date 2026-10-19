/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.event;

import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.shared.event.FastLoginAntiBotEvent;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

public class BukkitFastLoginAntiBotEvent extends Event implements FastLoginAntiBotEvent, Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final InetSocketAddress address;
    private final String username;
    private final Action action;
    private boolean cancelled;

    public BukkitFastLoginAntiBotEvent(InetSocketAddress address, String username, Action action) {
        // async = true because this event may fire from async packet handlers (ProtocolLib)
        super(true);

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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
