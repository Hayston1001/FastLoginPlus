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

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumNameResolverTest {

    private static final UUID EXPECTED =
            UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void parsesTheMojangIdFromTheLookupBody() {
        var resolver = new PremiumNameResolver(name -> "{\"id\":\"069a79f444e94726a5befca90e38aaf5\","
                + "\"name\":\"" + name + "\"}");

        assertEquals(Optional.of(EXPECTED), resolver.resolve("Notch"));
    }

    @Test
    void unknownNamesAndFailedLookupsResolveToNothing() {
        var missing = new PremiumNameResolver(name -> null);
        var emptyBody = new PremiumNameResolver(name -> "");
        var broken = new PremiumNameResolver(name -> {
            throw new IllegalStateException("offline");
        });

        assertTrue(missing.resolve("Notch").isEmpty());
        assertTrue(emptyBody.resolve("Notch").isEmpty());
        assertTrue(broken.resolve("Notch").isEmpty());
    }
}
