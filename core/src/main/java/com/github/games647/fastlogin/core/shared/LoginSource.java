/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import java.net.InetSocketAddress;

public interface LoginSource {

    void enableOnlinemode() throws Exception;

    void kick(String message);

    InetSocketAddress getAddress();

    /**
     * Returns true if {@link #kick(String)} was called on this source.
     * Used by the caller (e.g. NameCheckTask) to know whether to cancel
     * the intercepted packet instead of forwarding it to the vanilla server.
     *
     * <p>Default implementation returns {@code false}. Only ProtocolLib-based
     * sources need to override this, because they intercept packets at the
     * Netty level and must explicitly cancel the START packet after a kick
     * to prevent the vanilla server from sending its own disconnect message.
     *
     * @return true if kick was called
     */
    default boolean isKicked() {
        return false;
    }
}
