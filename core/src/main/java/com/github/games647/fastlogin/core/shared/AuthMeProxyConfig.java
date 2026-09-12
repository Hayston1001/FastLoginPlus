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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Editing helpers for the configuration of AuthMe's proxy plugins
 * ({@code AuthMeBungee} / {@code AuthMeVelocity}).
 *
 * <p>FastLoginPlus owns proxy-side premium verification. AuthMe's
 * {@code premium.keepOfflineUuidCompatibility=true} mode performs its own
 * login-phase handshake on the same connection, which installs a second
 * encryption layer on top of the one FastLoginPlus already established. The
 * two handshakes corrupt the connection (tracked as ISS-03 in the AuthMe 6.0.1
 * impact assessment), so FastLoginPlus pins the flag to {@code false}.
 *
 * <p>The file is edited with a single-line textual substitution rather than a
 * YAML round-trip: AuthMe generates a heavily commented configuration and a
 * deserialise/serialise cycle would discard every comment.
 */
public final class AuthMeProxyConfig {

    /**
     * AuthMe proxy config key selecting the backend UUID mode. {@code true}
     * keeps the name-derived offline UUID, {@code false} keeps the Mojang UUID.
     */
    public static final String OFFLINE_UUID_KEY = "keepOfflineUuidCompatibility";

    /**
     * Matches the value of the target key on its own line, capturing the
     * leading {@code key:} part and any optional quotes so that everything
     * else on the line (comments, indentation, CRLF) survives untouched.
     */
    private static final Pattern OFFLINE_UUID_TRUE = Pattern.compile(
            "(?m)^([ \\t]*" + OFFLINE_UUID_KEY + "[ \\t]*:[ \\t]*)(['\"]?)(true|True|TRUE)\\2");

    private AuthMeProxyConfig() {
    }

    /**
     * Rewrites {@code premium.keepOfflineUuidCompatibility} to {@code false} in
     * the given AuthMe proxy configuration file.
     *
     * <p>The write is atomic: the new content is staged next to the original and
     * then moved into place, so an interrupted write cannot leave a truncated
     * configuration behind.
     *
     * @param configFile the AuthMe proxy plugin's {@code config.yml}
     * @return true if the file was modified, false if it is missing or already {@code false}
     * @throws IOException if the file cannot be read or written
     */
    public static boolean forceOfflineUuidCompatibilityOff(Path configFile) throws IOException {
        if (configFile == null || !Files.isRegularFile(configFile)) {
            return false;
        }

        String content = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
        Matcher matcher = OFFLINE_UUID_TRUE.matcher(content);
        if (!matcher.find()) {
            return false;
        }

        String replacement = matcher.group(1) + matcher.group(2) + "false" + matcher.group(2);
        String updated = content.substring(0, matcher.start())
                + replacement
                + content.substring(matcher.end());

        Path tmp = configFile.resolveSibling(configFile.getFileName() + ".fastlogin.tmp");
        Files.write(tmp, updated.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, configFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            // some filesystems (e.g. certain network mounts) cannot move atomically
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }
}
