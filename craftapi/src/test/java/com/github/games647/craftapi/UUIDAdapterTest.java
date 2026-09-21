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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UUIDAdapterTest {

    private static final String DINNERBONE_OFFLINE_ID = "4d258a81-2358-3084-8166-05b9faccad80";
    private static final String DINNERBONE_PREMIUM_ID = "61699b2e-d327-4a01-9f1e-0ea8c3f06bc6";
    private static final String DINNERBONE_MOJANG_ID = "61699b2ed3274a019f1e0ea8c3f06bc6";

    private static final String JEB_OFFLINE_ID = "68f45697-675f-33db-abc6-9adc644d11aa";
    private static final String JEB_PREMIUM_ID = "853c80ef-3c37-49fd-aa49-938b674adae6";
    private static final String JEB_MOJANG_ID = "853c80ef3c3749fdaa49938b674adae6";

    @Test
    void testOfflineUUID() {
        UUID boneId = UUIDAdapter.generateOfflineId("Dinnerbone");
        assertEquals(boneId, UUID.fromString(DINNERBONE_OFFLINE_ID));

        UUID jebId = UUIDAdapter.generateOfflineId("Jeb_");
        assertEquals(jebId, UUID.fromString(JEB_OFFLINE_ID));
    }

    @Test
    void testMojangId() {
        UUID boneId = UUID.fromString(DINNERBONE_PREMIUM_ID);
        assertEquals(UUIDAdapter.toMojangId(boneId), DINNERBONE_MOJANG_ID);

        UUID jebId = UUID.fromString(JEB_PREMIUM_ID);
        assertEquals(UUIDAdapter.toMojangId(jebId), JEB_MOJANG_ID);
    }

    @Test
    void testNullMojangId() {
        Assertions.assertThrows(NullPointerException.class,
                () -> UUIDAdapter.toMojangId(null)
        );
    }

    @Test
    void testNullParse() {
        Assertions.assertThrows(NullPointerException.class,
                () -> UUIDAdapter.parseId(null)
        );
    }

    @Test
    void testNullGenerate() {
        Assertions.assertThrows(NullPointerException.class,
                () -> UUIDAdapter.generateOfflineId(null)
        );
    }

    @Test
    void testParsing() {
        UUID boneId = UUID.fromString(DINNERBONE_PREMIUM_ID);
        assertEquals(UUIDAdapter.parseId(DINNERBONE_MOJANG_ID), boneId);

        UUID jebId = UUID.fromString(JEB_PREMIUM_ID);
        assertEquals(UUIDAdapter.parseId(JEB_MOJANG_ID), jebId);
    }

    @Test
    void testParsingDashed() {
        UUID boneId = UUID.fromString(DINNERBONE_PREMIUM_ID);
        assertEquals(UUIDAdapter.parseDashedId(DINNERBONE_PREMIUM_ID), boneId);
    }

    @Test
    void testToString() {
        UUID boneId = UUID.fromString(DINNERBONE_PREMIUM_ID);
        assertEquals(UUIDAdapter.toString(boneId), DINNERBONE_PREMIUM_ID);
    }
}
