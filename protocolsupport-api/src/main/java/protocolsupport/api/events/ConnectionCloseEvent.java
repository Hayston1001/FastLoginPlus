/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package protocolsupport.api.events;

import protocolsupport.api.Connection;

/**
 * The connection-close event methods FLP calls. Signatures mirror the ProtocolSupport API.
 */
public abstract class ConnectionCloseEvent {

    public abstract Connection getConnection();
}
