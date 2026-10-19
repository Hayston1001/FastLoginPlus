/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.antibot;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable set of trusted IP addresses that bypass all anti-bot checks.
 */
public class TrustedIpSet {

    private final Set<InetAddress> trustedIps;

    public TrustedIpSet(Set<InetAddress> trustedIps) {
        this.trustedIps = Collections.unmodifiableSet(new HashSet<>(trustedIps));
    }

    /**
     * Check if the given address is in the trusted list.
     *
     * @param address the IP to check
     * @return true if trusted
     */
    public boolean isTrusted(InetAddress address) {
        return trustedIps.contains(address);
    }
}
