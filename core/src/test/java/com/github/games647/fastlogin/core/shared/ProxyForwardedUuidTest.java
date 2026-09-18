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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.UUIDAdapter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link ProxyForwardedUuid} (0.7.0/F24): the pre-login pre-create on Spigot fires on a
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
