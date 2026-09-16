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
package com.github.games647.fastlogin.bungee;

import com.github.games647.fastlogin.bungee.listener.ConnectListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that the shipped bungee classes only link against the published part of the proxy.
 *
 * <p><b>Why this is a test and not a convention.</b> This module compiles against a 2024
 * BungeeCord artifact, while the proxy's non-API half (everything outside {@code api},
 * {@code event} and {@code config}) is reorganised freely upstream — it moved packages once
 * already (BungeeCord #3855, 2025-07-14, which is what 0.7.0/F17 fixed). A compile-time
 * reference to such a class cannot be caught here: the compiler checks it against the old
 * artifact, succeeds, and the failure only appears on a user's newer proxy — where it takes the
 * whole plugin down at startup. Scanning the compiled classes closes that gap locally, and
 * failing here instead of on a user's server is the point.</p>
 *
 * <p>Names are collected in their JVM form ({@code net/md_5/bungee/connection/InitialHandler}).
 * A slash-separated name is what the runtime resolves as a type — superclasses, descriptor and
 * signature entries, bytecode operands. A dot-separated name is a string constant, i.e. a
 * deliberate runtime lookup such as {@code Class.forName}; those are the module's supported way
 * of reaching a non-API class and are therefore not reported.</p>
 */
class BungeePublicApiOnlyTest {

    /**
     * Proxy packages that are published as their own versioned artifacts, and are consequently
     * safe to link against: {@code bungeecord-api}, {@code bungeecord-event} and
     * {@code bungeecord-config} all carry release versions; the proxy jar does not.
     */
    private static final List<String> PUBLISHED_PACKAGES = Arrays.asList(
            "net/md_5/bungee/api/",
            "net/md_5/bungee/event/",
            "net/md_5/bungee/config/"
    );

    private static final String PROXY_ROOT = "net/md_5/bungee/";

    @Test
    void mainClassesOnlyLinkPublishedProxyPackages() throws IOException {
        Path classesDirectory = mainClassesDirectory();

        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(classesDirectory)) {
            classFiles = walk.filter(path -> path.toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }

        assertFalse(classFiles.isEmpty(), "no compiled classes found in " + classesDirectory);

        List<String> offenders = new ArrayList<>();
        boolean sawPublishedReference = false;
        for (Path classFile : classFiles) {
            for (String reference : referencedProxyClasses(Files.readAllBytes(classFile))) {
                if (isPublished(reference)) {
                    sawPublishedReference = true;
                } else {
                    offenders.add(classFile.getFileName() + " -> " + reference);
                }
            }
        }

        // Without this the scan could silently inspect nothing and still look green
        assertTrue(sawPublishedReference,
                "expected to find the proxy API references this module is known to use");

        assertTrue(offenders.isEmpty(), "these references sit in the proxy's non-API half, which"
                + " upstream moves without notice; reach them by name at runtime instead"
                + " (see ConnectListener.HANDLER_CLASS_NAME): " + offenders);
    }

    /**
     * Locates the directory surefire loaded the compiled main classes from.
     *
     * @return the main classes directory
     */
    private static Path mainClassesDirectory() {
        try {
            return Paths.get(ConnectListener.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | NullPointerException locationFailure) {
            throw new IllegalStateException("cannot locate the compiled main classes", locationFailure);
        }
    }

    /**
     * Extracts every {@code net/md_5/bungee/...} name appearing in a class file.
     *
     * <p>The bytes are read as ISO-8859-1 so that one byte maps to exactly one character; the
     * names being looked for are ASCII, which is all a constant pool entry holds for them.</p>
     *
     * @param classBytes the class file contents
     * @return the referenced names, in the JVM's slash-separated form
     */
    private static List<String> referencedProxyClasses(byte[] classBytes) {
        String content = new String(classBytes, StandardCharsets.ISO_8859_1);
        List<String> references = new ArrayList<>();

        int start = content.indexOf(PROXY_ROOT);
        while (start >= 0) {
            int end = start;
            while (end < content.length() && isNameCharacter(content.charAt(end))) {
                end++;
            }
            references.add(content.substring(start, end));
            start = content.indexOf(PROXY_ROOT, end);
        }

        return references;
    }

    private static boolean isNameCharacter(char character) {
        return character == '/' || character == '$' || character == '_'
                || Character.isLetterOrDigit(character);
    }

    private static boolean isPublished(String reference) {
        for (String publishedPackage : PUBLISHED_PACKAGES) {
            if (reference.startsWith(publishedPackage)) {
                return true;
            }
        }

        return false;
    }
}
