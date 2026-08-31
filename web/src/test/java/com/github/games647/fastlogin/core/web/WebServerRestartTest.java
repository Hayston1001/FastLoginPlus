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
package com.github.games647.fastlogin.core.web;

import static org.mockito.Mockito.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.github.games647.fastlogin.core.storage.SQLStorage;

/**
 * Lifecycle regression test for the web panel restart semantics
 * (audit F003).
 *
 * <p>The platform integrations (Bukkit /reload, BungeeCord and Velocity
 * restarts) now stop the panel on disable and start it again on enable.
 * Cross-process behaviour cannot be tested in-JVM, but the underlying
 * contract is: after {@link WebServer#stop()} released the port, a fresh
 * {@link WebServer#start(String, int, String)} must be able to bind the
 * same port again.</p>
 */
class WebServerRestartTest {

    private static final String TOKEN = "unit-test-token-0123456789abcdef";

    @Test
    void startAfterStopCanBindTheSamePortAgain() throws Exception {
        WebServer server = new WebServer(LoggerFactory.getLogger("WebServerRestartTest"),
                mock(SQLStorage.class), null, "test", null);

        // random port first, then rebind exactly the port the first run held
        server.start("127.0.0.1", 0, TOKEN);
        int firstPort = server.port();
        server.stop();

        server.start("127.0.0.1", firstPort, TOKEN);
        int reboundPort = server.port();
        server.stop();

        org.junit.jupiter.api.Assertions.assertEquals(firstPort, reboundPort,
                "second start must bind the same port the first run held");
    }

    @Test
    void stoppedServerRejectsFurtherRequests() throws Exception {
        WebServer server = new WebServer(LoggerFactory.getLogger("WebServerRestartTest"),
                mock(SQLStorage.class), null, "test", null);
        server.start("127.0.0.1", 0, TOKEN);
        int port = server.port();
        server.stop();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/players")).GET().build();

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> client.send(request, HttpResponse.BodyHandlers.ofString()),
                "requests to a stopped panel must fail (connection refused)");
    }
}
