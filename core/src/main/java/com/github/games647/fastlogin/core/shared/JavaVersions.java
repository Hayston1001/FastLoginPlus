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

/**
 * Java runtime version detection (0.6.0/F057).
 *
 * <p>The web panel stack (Javalin 7 / Jetty 12) is Java 17 bytecode while the
 * platform main classes stay on release 8. Platform code therefore must check
 * the JVM version <em>before</em> touching any web class — this helper is
 * Java 8 safe itself (it reads {@code java.class.version}, unlike
 * {@code Runtime.version()}, which would already throw
 * {@code NoSuchMethodError} on Java 8).</p>
 */
public final class JavaVersions {

    /**
     * Minimum Java feature version required by the web panel stack
     * (Javalin 7 hard requirement). Single source of truth — bump when
     * upgrading Javalin (e.g. a future Javalin 8).
     */
    public static final int MINIMUM_WEB_JAVA = 17;

    private JavaVersions() {
        // utility class
    }

    /**
     * Check whether the current JVM is at least the given Java feature version.
     *
     * @param feature the Java feature version to check against (e.g. 17)
     * @return true if the JVM is at least the given version
     */
    public static boolean isAtLeast(int feature) {
        try {
            // class-file major = feature + 44 (52=Java 8, 55=Java 11, 61=Java 17)
            return Double.parseDouble(System.getProperty("java.class.version", "0"))
                    >= feature + 44;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
