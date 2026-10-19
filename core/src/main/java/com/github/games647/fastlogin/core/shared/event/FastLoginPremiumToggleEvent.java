/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared.event;

import com.github.games647.fastlogin.core.storage.StoredProfile;

/**
 * This even represents the opt-in premium status change by request.
 */
public interface FastLoginPremiumToggleEvent {

    StoredProfile getProfile();

    PremiumToggleReason getReason();

    enum PremiumToggleReason {
        COMMAND_SELF,
        COMMAND_OTHER
    }
}
