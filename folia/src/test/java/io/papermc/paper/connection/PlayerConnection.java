/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package io.papermc.paper.connection;

import net.kyori.adventure.text.Component;

/** Test-only surface of the newer Paper API, absent from the compile-only server baseline. */
public interface PlayerConnection {
    void disconnect(Component reason);
}
