/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Decision;

/** Requires password ownership for both binding paths; verification chooses when to convert. */
final class BindingRequest {
    private BindingRequest() {
    }

    static boolean completeNow(Decision decision, boolean authenticated, AccountState state) {
        if (!authenticated) {
            throw FabricMessages.failure("binding-login-required");
        }
        if (decision != Decision.OFFLINE && decision != Decision.PREMIUM_VERIFIED) {
            throw FabricMessages.failure("binding-identity-required");
        }
        if (state != AccountState.PASSWORD && state != AccountState.BIND_PENDING) {
            throw FabricMessages.failure("binding-password-required");
        }
        return decision == Decision.PREMIUM_VERIFIED;
    }
}
