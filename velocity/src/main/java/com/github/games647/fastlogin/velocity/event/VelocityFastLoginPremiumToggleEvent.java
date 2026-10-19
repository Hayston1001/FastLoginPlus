/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.event;

import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;

public class VelocityFastLoginPremiumToggleEvent implements FastLoginPremiumToggleEvent {

    private final StoredProfile profile;
    private final PremiumToggleReason reason;

    public VelocityFastLoginPremiumToggleEvent(StoredProfile profile, PremiumToggleReason reason) {
        this.profile = profile;
        this.reason = reason;
    }

    @Override
    public StoredProfile getProfile() {
        return profile;
    }

    @Override
    public PremiumToggleReason getReason() {
        return reason;
    }
}
