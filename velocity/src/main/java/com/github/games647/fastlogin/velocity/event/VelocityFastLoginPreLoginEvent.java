/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.event;

import com.github.games647.fastlogin.core.shared.LoginSource;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;

public class VelocityFastLoginPreLoginEvent implements FastLoginPreLoginEvent {

    private final String username;
    private final LoginSource source;
    private final StoredProfile profile;

    public VelocityFastLoginPreLoginEvent(String username, LoginSource source, StoredProfile profile) {
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
}
