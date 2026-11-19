/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package protocolsupport.api.events;

import protocolsupport.api.Connection;

/**
 * The login-start event methods FLP calls. Signatures mirror the ProtocolSupport API.
 */
public abstract class PlayerLoginStartEvent {

    public abstract Connection getConnection();

    public abstract boolean isLoginDenied();

    public abstract void denyLogin(String message);

    public abstract void setOnlineMode(boolean onlineMode);
}
