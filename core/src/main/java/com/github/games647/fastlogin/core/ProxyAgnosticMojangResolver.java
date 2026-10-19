/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

import com.github.games647.craftapi.model.auth.Verification;
import com.github.games647.craftapi.resolver.MojangResolver;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;

/**
 * An extension to {@link MojangResolver} which allows connection using transparent reverse proxies.
 * The significant difference is that unlike MojangResolver from the CraftAPI implementation, which sends the
 * "ip" parameter when the hostIp parameter is an IPv4 address, but skips it for IPv6, this implementation
 * never sends it - effectively enabling transparent proxies to work.
 *
 * @author games647, Enginecrafter77
 */
public class ProxyAgnosticMojangResolver extends MojangResolver {

    @Override
    public Optional<Verification> hasJoined(String username, String serverHash, InetAddress hostIp)
        throws IOException {
        // a null address is defined as "do not send the ip parameter" by the base implementation
        return super.hasJoined(username, serverHash, null);
    }
}
