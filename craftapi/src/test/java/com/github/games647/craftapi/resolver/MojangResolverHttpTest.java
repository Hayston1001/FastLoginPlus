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
package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.model.auth.Verification;
import com.github.games647.craftapi.resolver.LocalHttp.Endpoints;
import com.github.games647.craftapi.resolver.LocalHttp.ProxyServer;
import com.github.games647.craftapi.resolver.http.RotatingProxySelector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline regression tests for the Mojang name lookup: every response is served by a local
 * {@link com.sun.net.httpserver.HttpServer}, so no test here touches the network.
 */
class MojangResolverHttpTest {

    private Endpoints endpoints;
    private MojangResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        endpoints = new Endpoints();
        resolver = new MojangResolver();
        resolver.uuidUrl = endpoints.primaryUrl();
        resolver.backupUuidUrl = endpoints.backupUrl();
    }

    @AfterEach
    void tearDown() {
        endpoints.close();
    }

    @Test
    void resolvesProfileFromPrimaryEndpoint() throws Exception {
        Optional<Profile> profile = resolver.findProfile("Notch");

        assertTrue(profile.isPresent());
        assertEquals(LocalHttp.PREMIUM_ID, profile.get().getId());
        assertEquals("Notch", profile.get().getName());
        assertEquals(1, endpoints.primaryRequests().size());
        // direct requests only carry the path - the absolute form appears for proxy requests
        assertTrue(endpoints.primaryRequests().get(0).endsWith("/primary/Notch"));
        assertTrue(endpoints.backupRequests().isEmpty());
        assertFalse(resolver.useBackupUuidUrl);
    }

    @Test
    void returnsEmptyOnNoContent() throws Exception {
        endpoints.setPrimary(name -> LocalHttp.Reply.status(204));

        assertFalse(resolver.findProfile("Notch").isPresent());
        assertTrue(endpoints.backupRequests().isEmpty());
    }

    @Test
    void returnsEmptyOnNotFound() throws Exception {
        endpoints.setPrimary(name -> LocalHttp.Reply.status(404));

        assertFalse(resolver.findProfile("Notch").isPresent());
        assertTrue(endpoints.backupRequests().isEmpty());
    }

    @Test
    void switchesToBackupEndpointOn403AndStaysThere() throws Exception {
        endpoints.setPrimary(name -> LocalHttp.Reply.status(403));

        assertTrue(resolver.findProfile("Notch").isPresent());
        assertEquals(1, endpoints.primaryRequests().size());
        assertEquals(1, endpoints.backupRequests().size());
        assertTrue(endpoints.backupRequests().get(0).endsWith("/backup/Notch"));
        assertTrue(resolver.useBackupUuidUrl);

        // sticky: the next uncached lookup must not even try the primary endpoint again
        assertTrue(resolver.findProfile("Herobrine").isPresent());
        assertEquals(1, endpoints.primaryRequests().size());
        assertEquals(2, endpoints.backupRequests().size());
        assertTrue(endpoints.backupRequests().get(1).endsWith("/backup/Herobrine"));
    }

    @Test
    void throwsIOExceptionWhenBothEndpointsAnswer403() {
        endpoints.setPrimary(name -> LocalHttp.Reply.status(403));
        endpoints.setBackup(name -> LocalHttp.Reply.status(403));

        IOException exception = assertThrows(IOException.class, () -> resolver.findProfile("Notch"));
        assertEquals("Both Mojang APIs returned 403 Forbidden", exception.getMessage());
    }

    @Test
    void throwsIOExceptionOnUnexpectedStatus() {
        endpoints.setPrimary(name -> LocalHttp.Reply.status(503));

        IOException exception = assertThrows(IOException.class, () -> resolver.findProfile("Notch"));
        assertTrue(exception.getMessage().contains("503"), exception.getMessage());
    }

    @Test
    void throwsIOExceptionOnMalformedSuccessResponse() {
        // 200 without an id must never be treated as a premium profile
        endpoints.setPrimary(name -> LocalHttp.Reply.malformed(name));
        assertThrows(IOException.class, () -> resolver.findProfile("Notch"));

        // 200 with an empty json object
        endpoints.setPrimary(name -> LocalHttp.Reply.raw(200, "{}"));
        assertThrows(IOException.class, () -> resolver.findProfile("Herobrine"));

        // 200 with a body that is not json at all
        endpoints.setPrimary(name -> LocalHttp.Reply.raw(200, "not-json"));
        assertThrows(IOException.class, () -> resolver.findProfile("Dinnerbone"));
    }

    @Test
    void retriesRateLimitedDirectRequestThroughProxyWithFullUrl() throws Exception {
        AtomicInteger primaryCalls = new AtomicInteger();
        endpoints.setPrimary(name -> primaryCalls.getAndIncrement() == 0
                ? LocalHttp.Reply.status(429)
                : LocalHttp.Reply.profile(name));

        try (ProxyServer proxy = new ProxyServer()) {
            resolver.setProxySelector(new RotatingProxySelector(Collections.singleton(proxy.asProxy())));

            Optional<Profile> profile = resolver.findProfile("Notch");

            assertTrue(profile.isPresent());
            // the retry goes through the proxy, so the endpoint itself only saw the direct attempt
            assertEquals(1, endpoints.primaryRequests().size());
            assertEquals(1, proxy.requests().size());
            // the proxy has to receive the complete url including the player name
            assertEquals(endpoints.primaryUrl() + "Notch", proxy.requests().get(0));
        }
    }

    @Test
    void throwsRateLimitExceptionWhenProxyIsLimitedAsWell() throws Exception {
        endpoints.setPrimary(name -> LocalHttp.Reply.status(429));

        try (ProxyServer proxy = new ProxyServer()) {
            proxy.setResponder(name -> LocalHttp.Reply.status(429));
            resolver.setProxySelector(new RotatingProxySelector(Collections.singleton(proxy.asProxy())));

            assertThrows(RateLimitException.class, () -> resolver.findProfile("Notch"));
            assertEquals(1, proxy.requests().size());
        }
    }

    @Test
    void throwsRateLimitExceptionWithoutAnyProxy() {
        endpoints.setPrimary(name -> LocalHttp.Reply.status(429));

        assertThrows(RateLimitException.class, () -> resolver.findProfile("Notch"));
    }


    @Test
    void hasJoinedSendsTheIpParameterOnlyWhenAnAddressIsGiven() throws Exception {
        endpoints.setPrimary(name -> LocalHttp.Reply.raw(200, verificationJson(name)));
        resolver.hasJoinedUrlRaw = endpoints.primaryUrl() + "hasJoined?username=%s&serverId=%s";
        resolver.hasJoinedUrlProxyCheck = endpoints.primaryUrl() + "hasJoined?username=%s&serverId=%s&ip=%s";

        Optional<Verification> withoutAddress = resolver.hasJoined("Notch", "server-hash", null);
        Optional<Verification> withAddress = resolver.hasJoined(
                "Dinnerbone", "server-hash", InetAddress.getByName("127.0.0.1"));

        assertTrue(withoutAddress.isPresent());
        assertTrue(withAddress.isPresent());
        assertEquals(2, endpoints.primaryRequests().size());

        String withoutIp = endpoints.primaryRequests().get(0);
        assertTrue(withoutIp.contains("username=Notch"), withoutIp);
        assertFalse(withoutIp.contains("&ip="), "a null address must not send the ip parameter: " + withoutIp);

        String withIp = endpoints.primaryRequests().get(1);
        assertTrue(withIp.contains("ip=127.0.0.1"), withIp);
    }

    @Test
    void hasJoinedReturnsEmptyInsteadOfCrashingOnMalformedBody() throws Exception {
        resolver.hasJoinedUrlRaw = endpoints.primaryUrl() + "hasJoined?username=%s&serverId=%s";

        // empty 200 body: gson yields null, which must become "not verified" instead of an NPE
        endpoints.setPrimary(name -> LocalHttp.Reply.raw(200, ""));
        assertFalse(resolver.hasJoined("Notch", "server-hash", null).isPresent());

        // no content
        endpoints.setPrimary(name -> LocalHttp.Reply.status(204));
        assertFalse(resolver.hasJoined("Notch", "server-hash", null).isPresent());

        // broken json is an I/O failure, not an unchecked exception
        endpoints.setPrimary(name -> LocalHttp.Reply.raw(200, "not-json"));
        assertThrows(IOException.class, () -> resolver.hasJoined("Notch", "server-hash", null));
    }

    @Test
    void concurrentForbiddenLookupsAllFallBackToTheBackupEndpoint() throws Exception {
        CountDownLatch bothLookupsArrived = new CountDownLatch(2);
        CountDownLatch backupReached = new CountDownLatch(1);
        AtomicInteger primaryCalls = new AtomicInteger();

        endpoints.setPrimary(name -> {
            bothLookupsArrived.countDown();
            awaitLatch(bothLookupsArrived);
            if (primaryCalls.incrementAndGet() == 1) {
                // answered right away: this is the lookup that flips the sticky flag
                return LocalHttp.Reply.status(403);
            }

            // the second one only gets its 403 once the first one already switched to the backup, which
            // is exactly the interleaving that used to be misread as "both endpoints returned 403"
            awaitLatch(backupReached);
            return LocalHttp.Reply.status(403);
        });
        endpoints.setBackup(name -> {
            backupReached.countDown();
            return LocalHttp.Reply.profile(name);
        });

        ExecutorService lookups = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<Profile>> first = lookups.submit(() -> resolver.findProfile("Notch"));
            Future<Optional<Profile>> second = lookups.submit(() -> resolver.findProfile("Herobrine"));

            assertTrue(first.get(10, TimeUnit.SECONDS).isPresent(), "a 403 has to fall back to the backup");
            assertTrue(second.get(10, TimeUnit.SECONDS).isPresent(),
                    "a concurrent 403 must not be mistaken for 'both endpoints returned 403'");
        } finally {
            lookups.shutdownNow();
        }

        assertEquals(2, endpoints.primaryRequests().size());
        assertEquals(2, endpoints.backupRequests().size());
    }

    @Test
    void consumesErrorResponsesSoThatTheConnectionCanBeReused() throws Exception {
        try (LocalHttp.ConnectionCountingEndpoint endpoint = new LocalHttp.ConnectionCountingEndpoint(
                LocalHttp.Reply.raw(429, "{\"error\":\"rate limited\"}"),
                LocalHttp.Reply.profile("Notch"))) {
            resolver.uuidUrl = endpoint.url() + "/primary/";

            // without a proxy the 429 surfaces as a rate limit error, but its body still has to be
            // consumed: an unread response drops the socket instead of returning it to the keep-alive pool
            assertThrows(RateLimitException.class, () -> resolver.findProfile("Notch"));
            assertTrue(resolver.findProfile("Notch").isPresent());

            assertEquals(2, endpoint.requests());
            assertEquals(1, endpoint.connections(),
                    "an unconsumed error response drops the socket, so the second lookup needs a new one");
        }
    }

    @Test
    void consumesErrorResponsesOfTheSessionServerAsWell() throws Exception {
        try (LocalHttp.ConnectionCountingEndpoint endpoint = new LocalHttp.ConnectionCountingEndpoint(
                LocalHttp.Reply.raw(404, "{\"error\":\"not found\"}"),
                LocalHttp.Reply.raw(200, verificationJson("Notch")))) {
            resolver.hasJoinedUrlRaw = endpoint.url() + "/session?username=%s&serverId=%s";

            // the 404 of the session server used to return before reading the response
            assertFalse(resolver.hasJoined("Notch", "hash", null).isPresent());
            assertTrue(resolver.hasJoined("Notch", "hash", null).isPresent());

            assertEquals(2, endpoint.requests());
            assertEquals(1, endpoint.connections(), "a 404 of the session server must be consumed as well");
        }
    }

    private static void awaitLatch(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IOException("timeout while coordinating the test server");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while coordinating the test server", interrupted);
        }
    }

    private static String verificationJson(String name) {
        return "{\"id\":\"" + UUIDAdapter.toMojangId(LocalHttp.PREMIUM_ID)
                + "\",\"name\":\"" + name + "\",\"properties\":[]}";
    }

    @Test
    void zeroLimitSendsEveryLookupThroughTheProxy() throws Exception {
        resolver.setMaxNameRequests(0);

        try (ProxyServer proxy = new ProxyServer()) {
            resolver.setProxySelector(new RotatingProxySelector(Collections.singleton(proxy.asProxy())));

            assertTrue(resolver.findProfile("Notch").isPresent());

            assertTrue(endpoints.primaryRequests().isEmpty(), "no direct lookup is allowed");
            assertEquals(1, proxy.requests().size());
            assertEquals(endpoints.primaryUrl() + "Notch", proxy.requests().get(0));
        }
    }

    @Test
    void rotatesThroughAllConfiguredProxies() throws Exception {
        resolver.setMaxNameRequests(0);

        try (ProxyServer first = new ProxyServer(); ProxyServer second = new ProxyServer()) {
            Set<Proxy> proxies = new LinkedHashSet<>();
            proxies.add(first.asProxy());
            proxies.add(second.asProxy());
            resolver.setProxySelector(new RotatingProxySelector(proxies));

            assertTrue(resolver.findProfile("Notch").isPresent());
            assertTrue(resolver.findProfile("Herobrine").isPresent());

            assertEquals(1, first.requests().size());
            assertEquals(1, second.requests().size());
            assertTrue(first.requests().get(0).endsWith("/primary/Notch"));
            assertTrue(second.requests().get(0).endsWith("/primary/Herobrine"));
        }
    }
}
