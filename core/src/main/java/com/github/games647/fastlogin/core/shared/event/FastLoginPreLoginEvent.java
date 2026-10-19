/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared.event;

import com.github.games647.fastlogin.core.shared.LoginSource;
import com.github.games647.fastlogin.core.storage.StoredProfile;

/**
 * This action represents the login attempt of a player before the plugin makes any online mode actions.
 */
public interface FastLoginPreLoginEvent {

    String getUsername();

    LoginSource getSource();

    StoredProfile getProfile();
}
