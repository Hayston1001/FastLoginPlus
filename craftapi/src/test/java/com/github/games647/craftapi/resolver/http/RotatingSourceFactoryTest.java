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
package com.github.games647.craftapi.resolver.http;

import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingSourceFactoryTest {

    private RotatingSourceFactory sslFactory;

    @BeforeEach
    void setUp() {
        sslFactory = new RotatingSourceFactory();
    }

    @Test
    void testDefault() throws Exception {
        try (Socket socket = sslFactory.createSocket()) {
            assertNotNull(socket.getLocalAddress());
            assertTrue(socket.getLocalAddress().isAnyLocalAddress());
        }
    }

    @Test
    void testRotating() throws Exception {
        List<InetAddress> localAddresses = new ArrayList<>();
        localAddresses.add(InetAddress.getByName("192.168.0.1"));
        localAddresses.add(InetAddress.getByName("192.168.0.2"));
        localAddresses.add(InetAddress.getByName("192.168.0.3"));

        sslFactory.setOutgoingAddresses(localAddresses);

        for (int i = 1; i <= 4; i++) {
            Optional<InetAddress> localAddress = sslFactory.getNextLocalAddress();
            assertTrue(localAddress.isPresent());
            assertEquals(localAddress.get(), localAddresses.get((i - 1) % localAddresses.size()));
        }
    }

    @Test
    void testCollectionModification() throws Exception {
        Collection<InetAddress> localAddresses = new ArrayList<>();
        localAddresses.add(InetAddress.getByName("192.168.0.1"));

        sslFactory.setOutgoingAddresses(localAddresses);
        localAddresses.clear();

        assertTrue(sslFactory.getNextLocalAddress().isPresent());
    }
}
