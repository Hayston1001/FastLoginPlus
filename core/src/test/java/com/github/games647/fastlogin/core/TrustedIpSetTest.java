/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

import com.github.games647.fastlogin.core.antibot.TrustedIpSet;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedIpSetTest {

    @Test
    void trustedIpShouldPass() throws UnknownHostException {
        InetAddress trusted = InetAddress.getByName("192.168.1.1");
        TrustedIpSet set = new TrustedIpSet(Collections.singleton(trusted));

        assertTrue(set.isTrusted(trusted));
    }

    @Test
    void untrustedIpShouldFail() throws UnknownHostException {
        InetAddress trusted = InetAddress.getByName("192.168.1.1");
        InetAddress other = InetAddress.getByName("10.0.0.1");
        TrustedIpSet set = new TrustedIpSet(Collections.singleton(trusted));

        assertFalse(set.isTrusted(other));
    }

    @Test
    void emptySetShouldRejectAll() throws UnknownHostException {
        TrustedIpSet set = new TrustedIpSet(Collections.emptySet());

        assertFalse(set.isTrusted(InetAddress.getByName("127.0.0.1")));
    }

    @Test
    void multipleTrustedIps() throws UnknownHostException {
        InetAddress ip1 = InetAddress.getByName("192.168.1.1");
        InetAddress ip2 = InetAddress.getByName("10.0.0.1");
        InetAddress ip3 = InetAddress.getByName("172.16.0.1");
        TrustedIpSet set = new TrustedIpSet(new HashSet<>(Arrays.asList(ip1, ip2, ip3)));

        assertTrue(set.isTrusted(ip1));
        assertTrue(set.isTrusted(ip2));
        assertTrue(set.isTrusted(ip3));
        assertFalse(set.isTrusted(InetAddress.getByName("8.8.8.8")));
    }
}
