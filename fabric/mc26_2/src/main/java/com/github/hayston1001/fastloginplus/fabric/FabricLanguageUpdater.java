/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.shared.message.LanguageFileKeys;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Versioned, line-preserving updates for the backend-local Fabric language files.
 * Existing comments and key order are retained; missing keys are appended and
 * only untouched English defaults are refreshed on a version upgrade.
 */
final class FabricLanguageUpdater {

    static final String VERSION_KEY = "_messages-version";
    private static final String ADDED_HEADER =
            "# === Keys below are auto-added by FastLoginPlus ===";

    private FabricLanguageUpdater() {
    }

    static Result update(Path file, Map<String, String> english, Map<String, String> localized,
                         Logger logger) {
        if (!Files.exists(file)) {
            return Result.none();
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            FlatYamlReader.Result parsed = FlatYamlReader.readMessages(lines);
            if (parsed.unsupportedLine() != 0
                    || parsed.values().values().stream().anyMatch(value -> !(value instanceof String))) {
                logger.warn("Language file {} is not a valid message file; leaving it unchanged",
                        file.getFileName());
                return Result.none();
            }

            int currentVersion = version(localized, english);
            int userVersion = parseVersion(parsed.values().get(VERSION_KEY));
            LinkedHashSet<String> known = knownKeys(english, localized);
            LinkedHashSet<String> present = LanguageFileKeys.read(lines);
            List<String> missing = known.stream().filter(key -> !present.contains(key)).toList();

            Map<String, String> refreshed = new LinkedHashMap<>();
            if (currentVersion > userVersion) {
                for (String key : known) {
                    Object userValue = parsed.values().get(key);
                    String englishValue = english.get(key);
                    String localizedValue = localized.get(key);
                    if (userValue instanceof String && englishValue != null && localizedValue != null
                            && !englishValue.equals(localizedValue)
                            && englishValue.equals(userValue)) {
                        refreshed.put(key, localizedValue);
                    }
                }
            }

            boolean changed = false;
            if (!refreshed.isEmpty()) {
                lines = replaceValues(lines, refreshed);
                changed = true;
                logger.info("Refreshed {} unchanged message values to messages v{} in {}",
                        refreshed.size(), currentVersion, file.getFileName());
            }
            if (!missing.isEmpty()) {
                lines.add("");
                lines.add(ADDED_HEADER);
                for (String key : missing) {
                    lines.add(render(key, value(key, localized, english)));
                }
                changed = true;
                logger.info("Added {} missing message keys to {}", missing.size(), file.getFileName());
            }

            int versionLine = versionLine(lines);
            if (currentVersion > 0 && currentVersion > userVersion) {
                if (versionLine >= 0) {
                    lines.set(versionLine, VERSION_KEY + ": " + currentVersion);
                } else {
                    // Keep the version marker at the end so existing comments and
                    // key order are not moved or split.
                    lines.add(VERSION_KEY + ": " + currentVersion);
                }
                changed = true;
            }

            if (changed) {
                write(file, lines);
            }
            report(LanguageFileKeys.compare(known, LanguageFileKeys.read(lines)), file, logger);
            return new Result(missing.size(), currentVersion > userVersion);
        } catch (IOException | RuntimeException failure) {
            logger.warn("Cannot update language file {}; leaving it unchanged", file, failure);
            return Result.none();
        }
    }

    private static List<String> replaceValues(List<String> lines, Map<String, String> changed) {
        List<String> output = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String key = topLevelKey(line);
            if (key == null || !changed.containsKey(key)) {
                output.add(line);
                continue;
            }
            output.add(render(key, changed.get(key)));
            int next = index + 1;
            while (next < lines.size() && !lines.get(next).isEmpty()
                    && Character.isWhitespace(lines.get(next).charAt(0))) {
                next++;
            }
            index = next - 1;
        }
        return output;
    }

    private static String topLevelKey(String line) {
        if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t'
                || line.charAt(0) == '#') {
            return null;
        }
        int colon = line.indexOf(':');
        if (colon < 1 || (colon + 1 < line.length()
                && !Character.isWhitespace(line.charAt(colon + 1)))) {
            return null;
        }
        String key = line.substring(0, colon);
        return key.matches("[A-Za-z0-9_.-]+") ? key : null;
    }

    private static int version(Map<String, String> localized, Map<String, String> english) {
        int value = parseVersion(localized.get(VERSION_KEY));
        return value != 0 ? value : parseVersion(english.get(VERSION_KEY));
    }

    private static int parseVersion(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                // A malformed marker is treated as unversioned.
            }
        }
        return 0;
    }

    private static LinkedHashSet<String> knownKeys(Map<String, String> english,
                                                   Map<String, String> localized) {
        LinkedHashSet<String> known = new LinkedHashSet<>(english.keySet());
        known.addAll(localized.keySet());
        known.removeIf(key -> key.startsWith("_"));
        return known;
    }

    private static String value(String key, Map<String, String> localized,
                                Map<String, String> english) {
        String value = localized.get(key);
        if (value == null || value.isBlank()) {
            value = english.getOrDefault(key, "");
        }
        return value;
    }

    private static int versionLine(List<String> lines) {
        int found = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith(VERSION_KEY + ":")) {
                found = index;
            }
        }
        return found;
    }

    private static String render(String key, String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return key + ": \"" + escaped + "\"";
    }

    private static void report(LanguageFileKeys.Diagnostics diagnostics, Path file, Logger logger) {
        if (!diagnostics.missing().isEmpty()) {
            logger.warn("Language file {} is missing {} message keys: {}", file.getFileName(),
                    diagnostics.missing().size(), String.join(", ", diagnostics.missing()));
        }
        if (!diagnostics.redundant().isEmpty()) {
            logger.warn("Language file {} has {} message keys not in the bundled defaults "
                            + "(kept, not deleted): {}", file.getFileName(),
                    diagnostics.redundant().size(), String.join(", ", diagnostics.redundant()));
        }
    }

    private static void write(Path file, List<String> lines) throws IOException {
        String output = String.join("\n", lines);
        if (!output.endsWith("\n")) {
            output += "\n";
        }
        Path parent = file.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent != null ? parent : file.toAbsolutePath(),
                "messages-", ".tmp");
        try {
            Files.writeString(temporary, output, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    record Result(int added, boolean versionUpdated) {
        static Result none() {
            return new Result(0, false);
        }
    }
}
