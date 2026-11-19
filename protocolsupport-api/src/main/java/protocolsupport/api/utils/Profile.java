/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package protocolsupport.api.utils;

import java.util.UUID;

/**
 * The connection profile methods FLP calls. Signatures mirror the ProtocolSupport API.
 */
public abstract class Profile {

    public abstract boolean isOnlineMode();

    public abstract String getName();

    public abstract UUID getUUID();
}
