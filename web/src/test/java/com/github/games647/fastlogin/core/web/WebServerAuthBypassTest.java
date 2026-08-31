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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;

/**
 * Regression tests for the WebUI authentication/rate-limit middleware (audit F001).
 *
 * <p>Starts a real {@link WebServer} on an ephemeral port and speaks plain HTTP
 * to it through the JDK HttpClient. The assertions encode the <em>correct</em>
 * contract: an unauthenticated (or over-limit) request must be rejected with a
 * proper error status and must NOT carry any business data in its body.</p>
 *
 * <p>These tests were the RED baseline of the audit fix plan: on the unpatched
 * middleware (which wrote the error status but did not interrupt the Javalin
 * pipeline) the first three tests failed because the endpoint handlers still
 * ran and overwrote the response with business data — see the RED evidence in
 * audit/0.6.0/reports/09-fix-recheck.md (W0). They are green since the F001
 * fix (0.6.0/F001).</p>
 */
class WebServerAuthBypassTest {

    private static final String TOKEN = "unit-test-token-0123456789abcdef";

    private SQLStorage storage;
    private WebServer server;
    private HttpClient client;
    private String baseUri;

    @BeforeEach
    void startServer() {
        StoredProfile alice = new StoredProfile(1L, UUID.randomUUID(), "Alice", false,
                FloodgateState.FALSE, "203.0.113.7", Instant.now());

        storage = mock(SQLStorage.class);
        when(storage.loadAllProfiles(anyInt(), anyInt())).thenReturn(List.of(alice));
        when(storage.countProfiles(any())).thenReturn(1);
        when(storage.getDatabaseType()).thenReturn("sqlite");

        server = new WebServer(LoggerFactory.getLogger("WebServerAuthBypassTest"),
                storage, null, "test", null);
        server.start("127.0.0.1", 0, TOKEN);
        baseUri = "http://127.0.0.1:" + server.port();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> get(String path, String bearerToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUri + path)).GET();
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }

        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void requestWithoutTokenIsRejectedAndLeaksNoPlayerData() throws Exception {
        HttpResponse<String> response = get("/api/players", null);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"error\""),
                "401 body must be the {\"error\": ...} contract (F052): " + response.body());
        assertFalse(response.body().contains("\"players\""),
                "player list leaked to unauthenticated request: " + response.body());
        assertFalse(response.body().contains("Alice"),
                "player name leaked to unauthenticated request: " + response.body());
    }

    @Test
    void requestWithWrongTokenIsRejectedAndLeaksNoPlayerData() throws Exception {
        HttpResponse<String> response = get("/api/players", "wrong-token-aaaaaaaaaaaaaaaaaa");

        assertEquals(401, response.statusCode());
        assertFalse(response.body().contains("\"players\""),
                "player list leaked with wrong token: " + response.body());
        assertFalse(response.body().contains("Alice"),
                "player name leaked with wrong token: " + response.body());
    }

    @Test
    void eleventhRequestPerSecondIsRateLimitedAndLeaksNoBusinessData() throws Exception {
        HttpResponse<String> overLimit = null;
        for (int i = 0; i < 11; i++) {
            HttpResponse<String> response = get("/api/status", TOKEN);
            if (i < 10) {
                assertEquals(200, response.statusCode(),
                        "request " + (i + 1) + " should pass the rate limiter");
            } else {
                overLimit = response;
            }
        }

        assertEquals(429, overLimit.statusCode(), "11th request in one second must be rate limited");
        assertFalse(overLimit.body().contains("databaseType"),
                "status data leaked on rate-limited request: " + overLimit.body());
    }

    @Test
    void requestWithCorrectTokenReturnsPlayerData() throws Exception {
        HttpResponse<String> response = get("/api/players", TOKEN);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Alice"),
                "valid token must see the player list: " + response.body());
    }
}
