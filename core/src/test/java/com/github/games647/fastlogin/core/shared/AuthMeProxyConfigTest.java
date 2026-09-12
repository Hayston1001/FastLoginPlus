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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that AuthMe's proxy config is pinned without disturbing anything else in the file.
 */
class AuthMeProxyConfigTest {

    /** Shape of the file AuthMe's ConfigMe writes (4-space indent under the parent key). */
    private static final String AUTHME_BUNGEE_CONFIG = String.join("\n",
            "# Reference configuration for the native AuthMe Bungee proxy plugin.",
            "",
            "autoLogin: false",
            "premium:",
            "    # Keep verified premium players on the backend offline UUID for plugin compatibility.",
            "    # When false, premium players keep their Mojang UUID on the backend instead.",
            "    # False uses Bungee's local online-mode handshake path and does not require PacketEvents.",
            "    keepOfflineUuidCompatibility: true",
            "",
            "proxySharedSecret: ''",
            "");

    @Test
    void rewritesTrueToFalse(@TempDir Path dir) throws IOException {
        Path config = write(dir, AUTHME_BUNGEE_CONFIG);

        assertTrue(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config));
        assertTrue(read(config).contains("    keepOfflineUuidCompatibility: false"));
        assertFalse(read(config).contains("keepOfflineUuidCompatibility: true"));
    }

    @Test
    void preservesEveryOtherLineIncludingCommentsAndBlankLines(@TempDir Path dir) throws IOException {
        Path config = write(dir, AUTHME_BUNGEE_CONFIG);

        AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config);

        String expected = AUTHME_BUNGEE_CONFIG
                .replace("    keepOfflineUuidCompatibility: true",
                        "    keepOfflineUuidCompatibility: false");
        assertEquals(expected, read(config));
    }

    @Test
    void isIdempotentWhenAlreadyFalse(@TempDir Path dir) throws IOException {
        String alreadyOff = AUTHME_BUNGEE_CONFIG
                .replace("keepOfflineUuidCompatibility: true", "keepOfflineUuidCompatibility: false");
        Path config = write(dir, alreadyOff);

        assertFalse(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config));
        assertEquals(alreadyOff, read(config));
    }

    @Test
    void reportsNoChangeWhenTheKeyIsAbsent(@TempDir Path dir) throws IOException {
        String withoutKey = "# unrelated\npremium:\n    autoLogin: false\n";
        Path config = write(dir, withoutKey);

        assertFalse(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config));
        assertEquals(withoutKey, read(config));
    }

    @Test
    void reportsNoChangeWhenFileIsMissing(@TempDir Path dir) throws IOException {
        assertFalse(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(dir.resolve("nope.yml")));
    }

    @Test
    void reportsNoChangeForNullPath() throws IOException {
        assertFalse(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(null));
    }

    @Test
    void keepsTrailingCommentOnTheSameLine(@TempDir Path dir) throws IOException {
        Path config = write(dir, "premium:\n  keepOfflineUuidCompatibility: true # pinned by FLP\n");

        assertTrue(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config));
        assertEquals("premium:\n  keepOfflineUuidCompatibility: false # pinned by FLP\n", read(config));
    }

    @Test
    void keepsCrlfLineEndings(@TempDir Path dir) throws IOException {
        Path config = write(dir,
                "premium:\r\n    keepOfflineUuidCompatibility: true\r\nautoLogin: false\r\n");

        assertTrue(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config));
        assertEquals("premium:\r\n    keepOfflineUuidCompatibility: false\r\nautoLogin: false\r\n",
                read(config));
    }

    @Test
    void handlesQuotedValueAndRestoresTheQuote(@TempDir Path dir) throws IOException {
        Path config = write(dir, "premium:\n    keepOfflineUuidCompatibility: \"true\"\n");

        assertTrue(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config));
        assertEquals("premium:\n    keepOfflineUuidCompatibility: \"false\"\n", read(config));
    }

    @Test
    void leavesAnUnexpectedValueAlone(@TempDir Path dir) throws IOException {
        // a non-boolean value is not ours to fix — reporting no change keeps the caller honest
        String weird = "premium:\n    keepOfflineUuidCompatibility: maybe\n";
        Path config = write(dir, weird);

        assertFalse(AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(config));
        assertEquals(weird, read(config));
    }

    private static Path write(Path dir, String content) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
