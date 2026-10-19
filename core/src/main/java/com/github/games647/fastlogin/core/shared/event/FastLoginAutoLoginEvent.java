/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared.event;

import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.storage.StoredProfile;

/**
 * This event fires if the plugin performs an auto login on the platform where the login plugin is
 * <p>
 * {@snippet :
 * @EventHandler()
 * public void onPlayerLogin(FastLoginAutoLoginEvent loginEvent) {
 *     StoredProfile profile = loginEvent.getProfile();
 *     LoginSession session = loginEvent.getSession();
 *
 *     System.out.println("Player: " + session.getUsername() +  " is about to be force logged in");
 * }
 * }
 */
public interface FastLoginAutoLoginEvent extends FastLoginCancellableEvent {
    LoginSession getSession();

    StoredProfile getProfile();
}
