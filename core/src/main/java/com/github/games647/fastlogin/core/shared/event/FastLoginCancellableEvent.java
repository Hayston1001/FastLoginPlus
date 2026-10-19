/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared.event;

public interface FastLoginCancellableEvent {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
