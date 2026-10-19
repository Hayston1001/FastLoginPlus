/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.event;

import com.github.games647.fastlogin.core.shared.LoginSource;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BukkitFastLoginPreLoginEvent extends Event implements FastLoginPreLoginEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String username;
    private final LoginSource source;
    private final StoredProfile profile;

    public BukkitFastLoginPreLoginEvent(String username, LoginSource source, StoredProfile profile) {
        super(true);

        this.username = username;
        this.source = source;
        this.profile = profile;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public LoginSource getSource() {
        return source;
    }

    @Override
    public StoredProfile getProfile() {
        return profile;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
