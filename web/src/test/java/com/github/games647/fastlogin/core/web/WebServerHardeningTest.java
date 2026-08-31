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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.IpBanManager;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.core.storage.StorageUnavailableException;
import com.google.common.base.Ticker;

/**
 * Regression tests for the P2 security hardening batch (audit W3):
 * F013 (CORS whitelist), F015/F016 (rate limiter table + race/monotonic
 * clock), F046/F012 (strict lookups, no INSERT for unknown players),
 * F050 (literal-only IP bans) and F010 (storage outage surfaces as 503).
 */
class WebServerHardeningTest {

    private static final String TOKEN = "unit-test-token-0123456789abcdef";

    private SQLStorage storage;
    private WebServer server;
    private HttpClient client;
    private String baseUri;

    @BeforeEach
    void startServer() {
        StoredProfile alice = new StoredProfile(1L, UUID.randomUUID(), "Alice", false,
                FloodgateState.FALSE, "203.0.113.7", java.time.Instant.now());

        storage = mock(SQLStorage.class);
        when(storage.loadAllProfiles(anyInt(), anyInt())).thenReturn(List.of(alice));
        when(storage.countProfiles(any())).thenReturn(1);
        when(storage.getDatabaseType()).thenReturn("sqlite");

        server = new WebServer(LoggerFactory.getLogger("WebServerHardeningTest"),
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

    private HttpResponse<String> get(String path, String bearerToken, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUri + path)).GET();
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }

        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUri + path))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ---- F013: CORS is opt-in, never anyHost ------------------------------

    @Test
    void crossOriginRequestGetsNoCorsHeaderWithoutWhitelist() throws Exception {
        HttpResponse<String> response = get("/api/players", TOKEN, "http://evil.example");

        assertEquals(200, response.statusCode());
        assertFalse(response.headers().firstValue("Access-Control-Allow-Origin").isPresent(),
                "ACAO must not be present without an explicit whitelist (F013)");
    }

    @Test
    void whitelistOnlyAllowsConfiguredOrigins() throws Exception {
        WebServer whitelisted = new WebServer(LoggerFactory.getLogger("WebServerHardeningTest"),
                storage, null, "test", null);
        whitelisted.start("127.0.0.1", 0, TOKEN, List.of("http://allowed.example"));

        try {
            HttpClient http = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + whitelisted.port();

            HttpRequest allowed = HttpRequest.newBuilder(URI.create(base + "/api/players"))
                    .header("Authorization", "Bearer " + TOKEN)
                    .header("Origin", "http://allowed.example")
                    .GET().build();
            HttpResponse<String> allowedResponse = http.send(allowed, HttpResponse.BodyHandlers.ofString());
            assertEquals("http://allowed.example",
                    allowedResponse.headers().firstValue("Access-Control-Allow-Origin").orElse(null),
                    "whitelisted origin must receive ACAO");

            HttpRequest evil = HttpRequest.newBuilder(URI.create(base + "/api/players"))
                    .header("Authorization", "Bearer " + TOKEN)
                    .header("Origin", "http://evil.example")
                    .GET().build();
            HttpResponse<String> evilResponse = http.send(evil, HttpResponse.BodyHandlers.ofString());
            assertFalse(evilResponse.headers().firstValue("Access-Control-Allow-Origin").isPresent(),
                    "non-whitelisted origin must not receive ACAO");
        } finally {
            whitelisted.stop();
        }
    }

    // ---- F015: per-IP rate-limit table is swept ---------------------------

    @Test
    void idleRateLimitEntriesAreEvictedBySweep() {
        for (int i = 0; i < 20; i++) {
            assertTrue(server.checkRateLimit("10.0.0." + i),
                    "fresh IP must pass the rate limiter");
        }
        assertEquals(20, server.trackedIpCount());

        // advance the monotonic clock beyond the idle threshold (5 minutes)
        server.sweepRateLimiters(System.nanoTime() + WebServer.IDLE_SWEEP_NANOS * 2);

        assertEquals(0, server.trackedIpCount(),
                "idle entries must be evicted by the sweep (F015)");
    }

    // ---- F016: window race + monotonic clock ------------------------------

    @Test
    void concurrentRequestsCannotExceedTheRateLimit() throws Exception {
        int threads = 100;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                if (server.checkRateLimit("198.51.100.7")) {
                    allowed.incrementAndGet();
                }
            });
            workers[i].start();
        }

        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        assertTrue(allowed.get() <= 10,
                "at most " + 10 + " requests per window must pass, got " + allowed.get()
                        + " (F016 race)");
    }

    private void runLockInMock() {
        when(storage.withNameLock(anyString(),
                org.mockito.ArgumentMatchers.<java.util.function.Supplier<Object>>any()))
                .thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
            });
    }

    // ---- F046/F012: strict lookups, no INSERT for unknown players ---------

    @Test
    void getUnknownPlayerReturns404() throws Exception {
        when(storage.findProfileByName("Ghost")).thenReturn(null);

        HttpResponse<String> response = request("GET", "/api/players/Ghost", null);

        assertEquals(404, response.statusCode());
        verify(storage, never()).save(any());
    }

    @Test
    void toggleUnknownPlayerWithoutListenerReturns404AndSavesNothing() throws Exception {
        when(storage.findProfileByName("Ghost")).thenReturn(null);
        runLockInMock();

        HttpResponse<String> response =
                request("PUT", "/api/players/Ghost/premium", "{}");

        assertEquals(404, response.statusCode(),
                "unknown players must not be created by the WebUI fallback (F046/F012)");
        verify(storage, never()).save(any(StoredProfile.class));
    }

    @Test
    void failedSaveIsReportedAsError() throws Exception {
        StoredProfile bob = new StoredProfile(2L, UUID.randomUUID(), "Bob", true,
                FloodgateState.FALSE, "203.0.113.8", java.time.Instant.now());
        when(storage.findProfileByName("Bob")).thenReturn(bob);
        when(storage.saveQuietly(any(StoredProfile.class))).thenReturn(false);
        runLockInMock();

        HttpResponse<String> response = request("PUT", "/api/players/Bob/cracked", "{}");

        assertEquals(500, response.statusCode(),
                "a failed save must not be reported as success (F012)");
        assertTrue(response.body().contains("error"));
    }

    // ---- F010: storage outage surfaces as 503 -----------------------------

    @Test
    void storageOutageReturns503InsteadOfEmptyList() throws Exception {
        when(storage.loadAllProfiles(anyInt(), anyInt()))
                .thenThrow(new StorageUnavailableException("down", null));
        when(storage.countProfiles(any()))
                .thenThrow(new StorageUnavailableException("down", null));

        HttpResponse<String> response = request("GET", "/api/players", null);

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("storage unavailable"),
                "outage must be visible in the body (F010)");
    }

    // ---- F050: ban endpoint takes literal IPs only ------------------------

    @Test
    void banRejectsHostnamesAndUnreasonableDurations() throws Exception {
        Ticker ticker = mock(Ticker.class);
        when(ticker.read()).thenReturn(0L);
        IpBanManager banManager = new IpBanManager(ticker);
        AntiBotService antiBot = mock(AntiBotService.class);
        when(antiBot.getIpBanManager()).thenReturn(banManager);

        WebServer banServer = new WebServer(LoggerFactory.getLogger("WebServerHardeningTest"),
                storage, antiBot, "test", null);
        banServer.start("127.0.0.1", 0, TOKEN);
        try {
            HttpClient http = HttpClient.newHttpClient();

            // hostname input must be rejected with 400 before any DNS lookup
            HttpResponse<String> hostname = post(http, banServer.port(),
                    "{\"ip\": \"evil.attacker.example\", \"duration\": 60}");
            assertEquals(400, hostname.statusCode(),
                    "hostname input must not trigger DNS resolution (F050)");

            HttpResponse<String> negative = post(http, banServer.port(),
                    "{\"ip\": \"203.0.113.9\", \"duration\": -5}");
            assertEquals(400, negative.statusCode(), "negative duration must be rejected");

            HttpResponse<String> huge = post(http, banServer.port(),
                    "{\"ip\": \"203.0.113.9\", \"duration\": "
                            + (AntiBotApiHandler.MAX_BAN_DURATION_SECONDS + 1) + "}");
            assertEquals(400, huge.statusCode(), "over-cap duration must be rejected");

            HttpResponse<String> valid = post(http, banServer.port(),
                    "{\"ip\": \"203.0.113.9\", \"duration\": 60}");
            assertEquals(200, valid.statusCode(), "literal IP ban must still work");
            assertTrue(banManager.banCount() == 1, "ban must be registered");
        } finally {
            banServer.stop();
        }
    }

    private HttpResponse<String> post(HttpClient http, int port, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/antibot/ban"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ---- W4: F025 type-safe ban body, F014 lang endpoint rate limited ----

    @Test
    void banRejectsNonStringIpField() throws Exception {
        Ticker ticker = mock(Ticker.class);
        when(ticker.read()).thenReturn(0L);
        IpBanManager banManager = new IpBanManager(ticker);
        AntiBotService antiBot = mock(AntiBotService.class);
        when(antiBot.getIpBanManager()).thenReturn(banManager);

        WebServer banServer = new WebServer(LoggerFactory.getLogger("WebServerHardeningTest"),
                storage, antiBot, "test", null);
        banServer.start("127.0.0.1", 0, TOKEN);
        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + banServer.port() + "/api/antibot/ban"))
                    .header("Authorization", "Bearer " + TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"ip\": 12345}"))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(),
                    "a non-string ip field must be rejected with 400 (F025), not 500");
        } finally {
            banServer.stop();
        }
    }

    @Test
    void languageEndpointIsRateLimitedToo() throws Exception {
        HttpResponse<String> overLimit = null;
        for (int i = 0; i < 11; i++) {
            HttpResponse<String> response = get("/api/lang/en", null, null);
            if (i == 10) {
                overLimit = response;
            }
        }

        assertEquals(429, overLimit.statusCode(),
                "the public language endpoint must be rate limited as well (F014)");
    }
}
