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
package com.github.games647.craftapi;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Golden vectors for the identity-critical parts of {@link UUIDAdapter}: the offline UUID of an
 * existing player and the storage format used by FastLoginPlus must never change silently.
 * The expected values were produced with the previously shipped {@code craftapi-0.8.1.jar}.
 */
class UUIDAdapterGoldenTest {

    private static final UUID NOTCH_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void offlineIdsMatchTheOnesOfTheShippedCraftApi() {
        assertOfflineId("Notch", "b50ad385-829d-3141-a216-7e7d7539ba7f");
        assertOfflineId("jeb_", "a762f560-4fce-3236-812a-b80efff0b62b");
        assertOfflineId("Dinnerbone", "4d258a81-2358-3084-8166-05b9faccad80");
        assertOfflineId("Herobrine", "25966168-dc9c-360c-8f32-ed022bfa1070");
        assertOfflineId("a", "52428a0e-1e30-3cb1-976c-e728b2614047");
        assertOfflineId("Player_123456789012345", "1697dacb-b019-3724-a6cf-22d56bec9469");
        assertOfflineId("\u73a9\u5bb6\u540d", "5bf26e8d-a2b6-3e83-9b74-40bf942cd037");
    }

    @Test
    void mojangIdFormatRoundTrips() {
        String undashed = UUIDAdapter.toMojangId(NOTCH_ID);

        assertEquals("069a79f444e94726a5befca90e38aaf5", undashed);
        assertEquals(NOTCH_ID, UUIDAdapter.parseId(undashed));
        assertEquals(NOTCH_ID, UUIDAdapter.parseDashedId("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> UUIDAdapter.parseId(""));
        assertThrows(IllegalArgumentException.class, () -> UUIDAdapter.parseId("xyz"));
        assertThrows(IllegalArgumentException.class,
                () -> UUIDAdapter.parseId("069a79f444e94726a5befca90e38aaf"));
    }

    private static void assertOfflineId(String name, String expectedDashed) {
        UUID offlineId = UUIDAdapter.generateOfflineId(name);

        assertEquals(UUID.fromString(expectedDashed), offlineId);
        String mojangFormat = UUIDAdapter.toMojangId(offlineId);
        assertEquals(expectedDashed.replace("-", ""), mojangFormat);
        assertEquals(offlineId, UUIDAdapter.parseId(mojangFormat));
    }
}
