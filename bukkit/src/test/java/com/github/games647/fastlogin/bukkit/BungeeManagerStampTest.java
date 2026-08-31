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
package com.github.games647.fastlogin.bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.5.0/F054: the backend stamps outgoing proxy-bound messages with its echoed
 * proxy allowlist (single ID / comma-joined set / empty set).
 */
class BungeeManagerStampTest {

    private final UUID first = UUID.fromString("0f6f2f66-bc5d-4d9a-a1f1-3b0a2c1c6b31");
    private final UUID second = UUID.fromString("1f6f2f66-bc5d-4d9a-a1f1-3b0a2c1c6b32");

    @Test
    void emptyProxySetStampsEmptyString() {
        assertEquals("", BungeeManager.stampFor(new HashSet<>()));
    }

    @Test
    void unknownProxySetStateStampsEmptyString() {
        // null models "support disabled / initialize not run" — never claim trust
        assertEquals("", BungeeManager.stampFor(null));
    }

    @Test
    void singleProxyIdStampsItself() {
        Set<UUID> proxyIds = new HashSet<>();
        proxyIds.add(first);

        assertEquals(first.toString(), BungeeManager.stampFor(proxyIds));
    }

    @Test
    void multipleProxyIdsStampCommaJoinedSet() {
        Set<UUID> proxyIds = new HashSet<>();
        proxyIds.add(first);
        proxyIds.add(second);

        String stamp = BungeeManager.stampFor(proxyIds);
        String[] parts = stamp.split(",");
        assertEquals(2, parts.length);
        assertTrue(parts[0].equals(first.toString()) || parts[0].equals(second.toString()));
        assertTrue(parts[1].equals(first.toString()) || parts[1].equals(second.toString()));
    }
}
