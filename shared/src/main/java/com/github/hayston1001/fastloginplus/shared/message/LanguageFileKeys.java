/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.shared.message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only comparison of a flat language file's keys against bundled defaults. */
public final class LanguageFileKeys {

    private static final String KEY_PATTERN = "[A-Za-z0-9_.-]+";

    private LanguageFileKeys() {
    }

    /**
     * Reads top-level keys in file order, ignoring comments, blank lines and
     * metadata keys. The file is not modified.
     *
     * @param lines raw UTF-8 language-file lines
     * @return message keys in the order they first appear
     */
    public static LinkedHashSet<String> read(List<String> lines) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String line : lines) {
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t'
                    || line.charAt(0) == '#') {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 1) {
                continue;
            }
            String key = line.substring(0, colon);
            if (key.matches(KEY_PATTERN) && (colon + 1 == line.length()
                    || Character.isWhitespace(line.charAt(colon + 1)))) {
                if (!key.startsWith("_")) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    /**
     * Compares expected message keys with the keys present in one user file.
     *
     * @param expected known message keys, typically the union of English and localized defaults
     * @param actual keys found in the user file
     * @return missing and redundant keys, each in a stable order
     */
    public static Diagnostics compare(Collection<String> expected, Collection<String> actual) {
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (String key : expected) {
            if (key != null && !key.startsWith("_")) {
                known.add(key);
            }
        }
        LinkedHashSet<String> present = new LinkedHashSet<>();
        for (String key : actual) {
            if (key != null && !key.startsWith("_")) {
                present.add(key);
            }
        }
        List<String> missing = known.stream().filter(key -> !present.contains(key))
                .collect(Collectors.toList());
        List<String> redundant = present.stream().filter(key -> !known.contains(key))
                .collect(Collectors.toList());
        return new Diagnostics(missing, redundant);
    }

    /**
     * Reads a user file and compares it without rewriting the file.
     *
     * @param file user language file
     * @param expected known message keys
     * @return missing and redundant keys
     * @throws IOException if the file cannot be read
     */
    public static Diagnostics inspect(Path file, Collection<String> expected) throws IOException {
        return compare(expected, read(Files.readAllLines(file, StandardCharsets.UTF_8)));
    }

    /** Missing and redundant message keys. */
    public static final class Diagnostics {
        private final List<String> missing;
        private final List<String> redundant;

        public Diagnostics(Collection<String> missing, Collection<String> redundant) {
            this.missing = Collections.unmodifiableList(new ArrayList<>(missing));
            this.redundant = Collections.unmodifiableList(new ArrayList<>(redundant));
        }

        public List<String> missing() {
            return missing;
        }

        public List<String> redundant() {
            return redundant;
        }

        public boolean isClean() {
            return missing.isEmpty() && redundant.isEmpty();
        }

        public Set<String> missingSet() {
            return new LinkedHashSet<>(missing);
        }

        public Set<String> redundantSet() {
            return new LinkedHashSet<>(redundant);
        }
    }
}
