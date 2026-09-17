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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SkinsRestorer keys its skin storage by the UUID the server sees, while FastLoginPlus knows the
 * verified Mojang UUID. With {@code premiumUuid: false} those two differ, so a lookup with the
 * Mojang UUID alone never finds the player's custom skin.
 */
class SkinsRestorerCompatTest {

    private static final UUID PREMIUM = UUID.fromString("272cb3e9-24d3-4dcd-b47c-4b786e7421f8");
    private static final UUID OFFLINE = UUID.fromString("08c85864-b91b-3ebc-a6ab-f95e7449c594");

    /**
     * The verified UUID is preferred (that is what SkinsRestorer is keyed by when the connection
     * carries premium UUIDs), the connection UUID is the fallback for the demoted case.
     */
    @Test
    void triesTheVerifiedUuidBeforeTheConnectionUuid() {
        assertEquals(Arrays.asList(PREMIUM, OFFLINE), SkinsRestorerCompat.skinCandidateUuids(PREMIUM, OFFLINE));
    }

    /**
     * A direct connection has the same UUID on both sides — one storage lookup is enough.
     */
    @Test
    void doesNotLookUpTheSameUuidTwice() {
        assertEquals(Collections.singletonList(PREMIUM), SkinsRestorerCompat.skinCandidateUuids(PREMIUM, PREMIUM));
    }

    /**
     * Either side may be unknown (no verified UUID, or no connection UUID yet); only what exists is
     * looked up, and nothing at all yields no lookup.
     */
    @Test
    void skipsMissingUuids() {
        assertEquals(Collections.singletonList(PREMIUM), SkinsRestorerCompat.skinCandidateUuids(PREMIUM, null));
        assertEquals(Collections.singletonList(OFFLINE), SkinsRestorerCompat.skinCandidateUuids(null, OFFLINE));
        assertEquals(Collections.emptyList(), SkinsRestorerCompat.skinCandidateUuids(null, null));
    }
}
