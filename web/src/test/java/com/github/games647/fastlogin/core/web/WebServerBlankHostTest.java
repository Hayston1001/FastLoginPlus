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
 * Blank web.host fallback regression test (F068).
 *
 * <p>The config comment before 0.6.0 claimed {@code web.host: ''} binds to all
 * interfaces, but the JDK resolves an empty hostname to the loopback address —
 * it was silently identical to the default {@code 127.0.0.1}. A blank value
 * must therefore start the panel normally (falling back to the default local
 * binding) instead of failing or behaving like a wildcard bind.</p>
 */
class WebServerBlankHostTest {

    private static final String TOKEN = "unit-test-token-0123456789abcdef";

    @Test
    void blankHostStartsPanelOnTheLocalDefault() throws Exception {
        WebServer server = new WebServer(LoggerFactory.getLogger("WebServerBlankHostTest"),
                mock(SQLStorage.class), null, "test", null);

        server.start("", 0, TOKEN);
        int port = server.port();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/players"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        server.stop();

        org.junit.jupiter.api.Assertions.assertEquals(401, response.statusCode(),
                "a blank web.host must fall back to the default local binding and serve there");
    }
}
