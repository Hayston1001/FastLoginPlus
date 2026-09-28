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
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.protocol.AccountStatusWire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounded backend ownership query, completed before Velocity chooses online mode. */
public final class AccountStatusClient {

    private final String host;
    private final int port;
    private final byte[] key;

    /**
     * Creates a client for one configured Fabric backend.
     * @param host backend account service host
     * @param port backend account service port
     * @param key shared 32-byte secret
     */
    public AccountStatusClient(String host, int port, byte[] key) {
        if (host == null || host.isEmpty() || port < 1 || port > 65535 || key == null || key.length != 32) {
            throw new IllegalArgumentException("Invalid Fabric account status configuration");
        }
        this.host = host;
        this.port = port;
        this.key = Arrays.copyOf(key, key.length);
    }

    /**
     * Reads EasyAuth ownership for this exact name; any failure aborts the login.
     * @param username proxy login name
     * @return verified backend account state
     * @throws IOException when the backend cannot be reached or authenticated
     */
    public AccountStatusWire.Result query(String username) throws IOException {
        String nonce = AccountStatusWire.nonce();
        String request = AccountStatusWire.request(username, nonce, key);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(2500);
            socket.getOutputStream().write((request + "\n").getBytes(StandardCharsets.US_ASCII));
            String line = readLine(socket);
            try {
                return AccountStatusWire.parseResponse(line, nonce, key);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Fabric account response was not authenticated", invalid);
            }
        }
    }

    private static String readLine(Socket socket) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int next; (next = socket.getInputStream().read()) != -1;) {
            if (next == '\n') {
                return bytes.toString(StandardCharsets.US_ASCII.name());
            }
            if (next < 32 || next > 126 || bytes.size() >= 160) {
                throw new IOException("Malformed Fabric account response");
            }
            bytes.write(next);
        }
        throw new IOException("Incomplete Fabric account response");
    }
}
