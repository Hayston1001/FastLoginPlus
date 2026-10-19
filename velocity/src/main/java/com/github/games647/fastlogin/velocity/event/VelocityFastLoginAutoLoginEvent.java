/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.event;

import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.velocitypowered.api.event.ResultedEvent;

import java.util.Objects;

public class VelocityFastLoginAutoLoginEvent
    implements FastLoginAutoLoginEvent, ResultedEvent<ResultedEvent.GenericResult> {

    private final LoginSession session;
    private final StoredProfile profile;
    private boolean cancelled;

    public VelocityFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile) {
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


    @Override
    public GenericResult getResult() {
        return cancelled ? GenericResult.denied() : GenericResult.allowed();
    }

    @Override
    public void setResult(GenericResult result) {
        cancelled = Objects.requireNonNull(result) != GenericResult.allowed();
    }
}
