/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
