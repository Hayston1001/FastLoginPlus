/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.UUIDAdapter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link ProxyForwardedUuid}: the pre-login pre-create on Spigot fires on a
 * version-4 UUID, which is the only shape a proxy-forwarded Mojang UUID can have — offline
 * UUIDs are name-derived and therefore version 3.
 */
class ProxyForwardedUuidTest {

    @Test
    void acceptsForwardedMojangUuid() {
        UUID mojangUuid = UUID.randomUUID();
        assertTrue(ProxyForwardedUuid.isForwardedMojangUuid(mojangUuid, "TestUser"));
    }

    @Test
    void rejectsOfflineUuid() {
        UUID offlineUuid = UUIDAdapter.generateOfflineId("TestUser");
        assertFalse(ProxyForwardedUuid.isForwardedMojangUuid(offlineUuid, "TestUser"));
    }

    @Test
    void rejectsNullInputs() {
        assertFalse(ProxyForwardedUuid.isForwardedMojangUuid(null, "TestUser"));
        assertFalse(ProxyForwardedUuid.isForwardedMojangUuid(UUID.randomUUID(), null));
        assertFalse(ProxyForwardedUuid.isForwardedMojangUuid(null, null));
    }

    @Test
    void rejectsNonVersionFourUuidOfAnotherName() {
        // A v3 UUID for a *different* name is still name-derived - it must not count as
        // forwarded, otherwise a cracked player could be pre-created as premium.
        UUID otherOfflineUuid = UUIDAdapter.generateOfflineId("SomeoneElse");
        assertFalse(ProxyForwardedUuid.isForwardedMojangUuid(otherOfflineUuid, "TestUser"));
    }

    @Test
    void offlineUuidDerivationStaysVersionThree() {
        // Guards the assumption the whole predicate rests on.
        assertTrue(UUIDAdapter.generateOfflineId("TestUser").version() == 3);
    }
}
