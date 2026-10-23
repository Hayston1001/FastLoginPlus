/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Orders EasyAuth login checks, persistence and current-session completion. */
final class EasyAuthLoginFlow {

    private EasyAuthLoginFlow() {
    }

    record LoginContext(ZonedDateTime now, ZonedDateTime lastKick, long resetTimeout, String ip, String oldIp) {
    }

    static void authenticate(Class<?> entryType, Object entry, UUID verifiedUuid, LoginContext context,
                              EasyAuthAccountUpdate.StorageUpdate update, Runnable finish) {
        checkContext(context);
        EasyAuthAccountUpdate.authenticate(entryType, entry, verifiedUuid, context.now(), context.ip(),
                update);
        finish.run();
    }

    static void authenticateBedrock(Class<?> entryType, Object entry, LoginContext context,
                                    EasyAuthAccountUpdate.StorageUpdate update, Runnable finish) {
        checkContext(context);
        EasyAuthAccountUpdate.authenticateBedrock(entryType, entry, context.now(), context.ip(), update);
        finish.run();
    }

    private static void checkContext(LoginContext context) {
        if (context.lastKick() == null
                || context.lastKick().plusSeconds(context.resetTimeout()).isAfter(context.now())) {
            throw new IllegalStateException("EasyAuth account is in a login timeout");
        }
        if (context.ip() == null || context.ip().isEmpty() || context.oldIp() == null) {
            throw new IllegalStateException("EasyAuth did not resolve a player IP");
        }
    }
}
