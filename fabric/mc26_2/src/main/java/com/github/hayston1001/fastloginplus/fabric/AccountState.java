/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.util.UUID;

/**
 * Classification of the local EasyAuth account behind one login name. The
 * backend reads this state itself and decides the login method; nothing here is
 * exposed to the proxy.
 */
enum AccountState {

    /** No account: an unclaimed name. */
    OPEN,
    /** Password account, still offline-owned. */
    PASSWORD,
    /** Premium-bound account (onlineAccount=TRUE), with any password dormant. */
    BOUND,
    /** Account fields contradict the name or the expected shape. */
    CONFLICT;

    /** Account state plus the bound premium UUID, which only BOUND carries. */
    record Snapshot(AccountState state, UUID uuid) {
    }
}
