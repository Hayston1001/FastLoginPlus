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

import com.github.games647.craftapi.resolver.LocalHttp.Endpoints;
import com.github.games647.craftapi.resolver.LocalHttp.ProxyServer;
import com.github.games647.craftapi.resolver.http.RotatingProxySelector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline tests for the {@code mojang-request-limit} handling.  The old implementation turned every
 * configured value into 600 and never rebuilt the limiter, so both the clamp and the routing matter.
 */
class MojangResolverRateLimitTest {

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
    void clampsConfiguredLimitIntoValidRange() {
        resolver.setMaxNameRequests(300);
        assertEquals(300, resolver.getMaxNameRequests());

        resolver.setMaxNameRequests(MojangResolver.MAX_NAME_REQUESTS_LIMIT);
        assertEquals(MojangResolver.MAX_NAME_REQUESTS_LIMIT, resolver.getMaxNameRequests());

        resolver.setMaxNameRequests(0);
        assertEquals(0, resolver.getMaxNameRequests());

        resolver.setMaxNameRequests(-7);
        assertEquals(0, resolver.getMaxNameRequests());

        resolver.setMaxNameRequests(MojangResolver.MAX_NAME_REQUESTS_LIMIT + 1);
        assertEquals(MojangResolver.MAX_NAME_REQUESTS_LIMIT, resolver.getMaxNameRequests());
    }

    @Test
    void onlyCacheMissesConsumeTheLimit() throws Exception {
        resolver.setMaxNameRequests(1);

        assertTrue(resolver.findProfile("Notch").isPresent());
        // the second lookup is served from the cache and must not consume a slot
        assertTrue(resolver.findProfile("Notch").isPresent());
        assertEquals(1, endpoints.primaryRequests().size());
    }

    @Test
    void firstLookupIsDirectAndSecondUsesTheProxy() throws Exception {
        resolver.setMaxNameRequests(1);

        try (ProxyServer proxy = new ProxyServer()) {
            resolver.setProxySelector(new RotatingProxySelector(Collections.singleton(proxy.asProxy())));

            assertTrue(resolver.findProfile("Notch").isPresent());
            assertEquals(1, endpoints.primaryRequests().size());
            assertTrue(proxy.requests().isEmpty());

            assertTrue(resolver.findProfile("Herobrine").isPresent());
            assertEquals(1, endpoints.primaryRequests().size(),
                    "an exhausted limit must not send another direct lookup");
            assertEquals(1, proxy.requests().size());
            assertEquals(endpoints.primaryUrl() + "Herobrine", proxy.requests().get(0));
        }
    }

    @Test
    void secondLookupIsRateLimitedWithoutProxy() throws Exception {
        resolver.setMaxNameRequests(1);

        assertTrue(resolver.findProfile("Notch").isPresent());
        assertThrows(RateLimitException.class, () -> resolver.findProfile("Herobrine"));
    }
}
