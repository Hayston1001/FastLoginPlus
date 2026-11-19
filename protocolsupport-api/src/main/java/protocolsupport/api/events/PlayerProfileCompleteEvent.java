/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package protocolsupport.api.events;

import java.util.UUID;

import protocolsupport.api.Connection;

/**
 * The profile-complete event methods FLP calls. Signatures mirror the ProtocolSupport API.
 */
public abstract class PlayerProfileCompleteEvent {

    public abstract Connection getConnection();

    public abstract String getForcedName();

    public abstract void setForcedUUID(UUID uuid);
}
