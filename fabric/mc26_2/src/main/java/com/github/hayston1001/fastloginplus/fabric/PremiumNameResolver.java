/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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
package com.github.hayston1001.fastloginplus.fabric;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a premium name to its Mojang UUID when a binding is created, so the
 * queued entry can pin the exact premium identity it is for. A name without a
 * premium identity has nothing to bind to.
 */
final class PremiumNameResolver {

    private static final Pattern MOJANG_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final String ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Function<String, String> lookup;

    PremiumNameResolver(Function<String, String> lookup) {
        this.lookup = lookup;
    }

    /**
     * Resolver backed by Mojang's public name-to-UUID endpoint.
     *
     * @return a resolver making live Mojang lookups
     */
    static PremiumNameResolver mojang() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        return new PremiumNameResolver(name -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + name))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200 ? response.body() : null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException | IllegalArgumentException unavailable) {
                return null;
            }
        });
    }

    /**
     * Looks up the premium UUID of a name.
     *
     * @param name exact player name
     * @return the Mojang UUID, or empty when the name has no premium identity
     *         or the lookup is unavailable
     */
    Optional<UUID> resolve(String name) {
        String body;
        try {
            body = lookup.apply(name);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        if (body == null) {
            return Optional.empty();
        }
        Matcher matcher = MOJANG_ID.matcher(body);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String hex = matcher.group(1);
        return Optional.of(new UUID(
                Long.parseUnsignedLong(hex.substring(0, 16), 16),
                Long.parseUnsignedLong(hex.substring(16), 16)));
    }
}
