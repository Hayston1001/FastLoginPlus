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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary tests for the Java version gate (audit F057).
 *
 * <p>The detection must work off {@code java.class.version} (class-file major
 * = feature + 44) so the gate itself runs on Java 8 JVMs. The property is
 * overridden per test to simulate old/classic class-file versions.</p>
 */
class JavaVersionsTest {

    private static final String PROPERTY = "java.class.version";

    @AfterEach
    void restoreProperty() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void java8ClassVersionIsBelowWebGate() {
        System.setProperty(PROPERTY, "52.0");

        assertTrue(JavaVersions.isAtLeast(8), "52.0 = Java 8");
        assertFalse(JavaVersions.isAtLeast(11), "52.0 must not count as Java 11");
        assertFalse(JavaVersions.isAtLeast(JavaVersions.MINIMUM_WEB_JAVA),
                "the web gate (17) must be closed on Java 8");
    }

    @Test
    void java11ClassVersionIsBelowWebGate() {
        System.setProperty(PROPERTY, "55.0");

        assertTrue(JavaVersions.isAtLeast(11), "55.0 = Java 11");
        assertFalse(JavaVersions.isAtLeast(JavaVersions.MINIMUM_WEB_JAVA),
                "the web gate (17) must be closed on Java 11");
    }

    @Test
    void java17ClassVersionOpensWebGate() {
        System.setProperty(PROPERTY, "61.0");

        assertTrue(JavaVersions.isAtLeast(17), "61.0 = Java 17");
        assertFalse(JavaVersions.isAtLeast(21), "61.0 must not count as Java 21");
    }

    @Test
    void java21And25ClassVersionsOpenWebGate() {
        System.setProperty(PROPERTY, "65.0");
        assertTrue(JavaVersions.isAtLeast(JavaVersions.MINIMUM_WEB_JAVA), "65.0 = Java 21");

        System.setProperty(PROPERTY, "69.0");
        assertTrue(JavaVersions.isAtLeast(JavaVersions.MINIMUM_WEB_JAVA), "69.0 = Java 25");
    }

    @Test
    void unparsableClassVersionClosesTheGate() {
        System.setProperty(PROPERTY, "not-a-number");

        assertFalse(JavaVersions.isAtLeast(JavaVersions.MINIMUM_WEB_JAVA),
                "unparsable class version must fail closed");
    }
}
