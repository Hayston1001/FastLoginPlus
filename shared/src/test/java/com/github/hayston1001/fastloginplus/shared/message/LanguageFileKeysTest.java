/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.shared.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageFileKeysTest {

    @TempDir
    Path directory;

    @Test
    void readsKeysInOrderAndIgnoresCommentsMetadataAndNestedContent() {
        LinkedHashSet<String> keys = LanguageFileKeys.read(Arrays.asList(
                "# header",
                "",
                "_messages-version: 2",
                "zeta: value",
                "  nested: ignored",
                "# middle",
                "alpha:",
                "beta: value"));

        assertEquals(Arrays.asList("zeta", "alpha", "beta"), new ArrayList<>(keys));
    }

    @Test
    void reportsMissingAndRedundantKeysWithoutChangingTheFile() throws Exception {
        Path file = directory.resolve("messages_zh.yml");
        String original = "# Keep this comment\nversioned: old\nobsolete: keep me\n";
        Files.write(file, original.getBytes(StandardCharsets.UTF_8));

        LanguageFileKeys.Diagnostics diagnostics = LanguageFileKeys.inspect(file,
                Arrays.asList("versioned", "missing", "_messages-version"));

        assertEquals(Arrays.asList("missing"), diagnostics.missing());
        assertEquals(Arrays.asList("obsolete"), diagnostics.redundant());
        assertEquals(original, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void metadataKeysAreNotReportedAsMissingOrRedundant() {
        LanguageFileKeys.Diagnostics diagnostics = LanguageFileKeys.compare(
                Arrays.asList("message", "_messages-version"),
                Arrays.asList("message", "_private-metadata"));

        assertTrue(diagnostics.isClean());
    }
}
