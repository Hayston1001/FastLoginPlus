/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
