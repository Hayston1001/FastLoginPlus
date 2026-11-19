/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package protocolsupport.api;

import java.net.InetSocketAddress;

import protocolsupport.api.utils.Profile;

/**
 * The connection methods FLP calls. Signatures mirror the ProtocolSupport API.
 */
public abstract class Connection {

    public abstract InetSocketAddress getRawAddress();

    public abstract Profile getProfile();
}
