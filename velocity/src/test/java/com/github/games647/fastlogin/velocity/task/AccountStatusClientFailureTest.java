/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 */
package com.github.games647.fastlogin.velocity.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountStatusClientFailureTest {

    private static final byte[] KEY = new byte[32];

    @Test
    void unavailableBackendFailsTheQuery() throws IOException {
        int port;
        try (ServerSocket reserved = new ServerSocket(0)) {
            port = reserved.getLocalPort();
        }
        assertThrows(IOException.class,
                () -> new AccountStatusClient("127.0.0.1", port, KEY).query("Alex"));
    }

    @Test
    void unauthenticatedBackendReplyFailsTheQuery() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            Thread responder = new Thread(() -> {
                try (var socket = listener.accept()) {
                    socket.getOutputStream().write("OPEN\n".getBytes(StandardCharsets.US_ASCII));
                } catch (IOException error) {
                    throw new AssertionError(error);
                }
            });
            responder.start();
            assertThrows(IOException.class, () -> new AccountStatusClient("127.0.0.1",
                    listener.getLocalPort(), KEY).query("Alex"));
            responder.join();
        }
    }
}
