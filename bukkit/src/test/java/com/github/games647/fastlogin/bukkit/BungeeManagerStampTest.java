/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * the backend stamps outgoing proxy-bound messages with its echoed
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
