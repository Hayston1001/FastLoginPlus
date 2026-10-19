/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.event;

import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.api.plugin.Cancellable;
import net.md_5.bungee.api.plugin.Event;

public class BungeeFastLoginAutoLoginEvent extends Event implements FastLoginAutoLoginEvent, Cancellable {

    private final LoginSession session;
    private final StoredProfile profile;
    private boolean cancelled;

    public BungeeFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile) {
        this.session = session;
        this.profile = profile;
    }

    @Override
    public LoginSession getSession() {
        return session;
    }

    @Override
    public StoredProfile getProfile() {
        return profile;
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
