/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
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
import java.util.Objects;
import java.util.Set;

/**
 * Repairs versioned language upgrades, including values left at the English default,
 * without replacing customized or unknown message values. Existing comments and key
 * order are retained; only changed value lines and appended keys are written.
 */
final class LanguageFileUpdater {
    static final String VERSION_KEY = "_messages-version";

    private LanguageFileUpdater() {
    }

    static int version(Configuration messages) {
        Object value = messages.get(VERSION_KEY);
        return value instanceof Integer ? (Integer) value : 0;
    }

    static Result update(Path file, Configuration english, Configuration localized,
                         int currentVersion, Configuration previousLocalized) throws IOException {
        if (!Files.exists(file)) {
            return new Result(0, 0);
        }
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        Configuration user;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            user = provider.load(reader);
        }
        int userVersion = version(user);
        boolean upgrading = currentVersion > userVersion;
        Set<String> userKeys = new LinkedHashSet<>(user.getKeys());
        Set<String> localKeys = new LinkedHashSet<>(localized.getKeys());
        Set<String> defaults = new LinkedHashSet<>(english.getKeys());
        defaults.addAll(localKeys);
        int added = 0;
        int refreshed = 0;
        Configuration additions = new Configuration();
        Map<String, String> changed = new LinkedHashMap<>();
        for (String key : defaults) {
            if (key.startsWith("_")) {
                continue;
            }
            Object translated = localKeys.contains(key) ? localized.get(key) : english.get(key);
            if (translated == null) {
                continue;
            }
            if (!userKeys.contains(key)) {
                additions.set(key, translated);
                added++;
            } else if (upgrading && previousLocalized != null && translated instanceof String
                    && user.get(key) instanceof String
                    && !Objects.equals(user.get(key), translated)
                    && (Objects.equals(user.get(key), previousLocalized.get(key))
                    || Objects.equals(user.get(key), english.get(key)))) {
                changed.put(key, (String) translated);
                refreshed++;
            }
        }
        if (added == 0 && refreshed == 0 && !upgrading) {
            return new Result(0, 0);
        }
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        if (!changed.isEmpty()) {
            lines = replaceValues(lines, changed);
        }
        if (upgrading) {
            setVersion(lines, currentVersion);
        }
        StringBuilder output = new StringBuilder(String.join("\n", lines));
        if (!lines.isEmpty() && !output.toString().endsWith("\n")) {
            output.append('\n');
        }
        if (added > 0) {
            StringWriter appended = new StringWriter();
            provider.save(additions, appended);
            output.append("\n# === Keys below are auto-added by FastLoginPlus ===\n").append(appended);
        }
        write(file, output.toString());
        return new Result(added, refreshed);
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
            output.addAll(replacement(line, changed.get(key)));
            boolean block = isBlockValue(line);
            int next = index + 1;
            while (next < lines.size() && !lines.get(next).isEmpty()
                    && Character.isWhitespace(lines.get(next).charAt(0))) {
                next++;
            }
            if (block) {
                while (next < lines.size() && lines.get(next).isEmpty()) {
                    next++;
                }
            }
            index = next - 1;
        }
        return output;
    }

    /**
     * Replaces only the key's own line(s), leaving surrounding comments and order intact.
     *
     * @param line the existing top-level key line
     * @param value the replacement value
     * @return replacement lines for the key and, for multiline values, its block
     */
    private static List<String> replacement(String line, String value) {
        int colon = line.indexOf(':');
        String prefix = line.substring(0, colon + 1);
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.indexOf('\n') < 0) {
            return java.util.Collections.singletonList(prefix + " " + quote(normalized));
        }
        List<String> output = new ArrayList<>();
        output.add(prefix + " " + (normalized.endsWith("\n") ? "|" : "|-"));
        String[] parts = normalized.split("\n", -1);
        int count = normalized.endsWith("\n") ? parts.length - 1 : parts.length;
        for (int index = 0; index < count; index++) {
            output.add("  " + parts[index]);
        }
        return output;
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static void setVersion(List<String> lines, int version) {
        for (int index = 0; index < lines.size(); index++) {
            String key = topLevelKey(lines.get(index));
            if (VERSION_KEY.equals(key)) {
                lines.set(index, VERSION_KEY + ": " + version);
                return;
            }
        }
        lines.add(VERSION_KEY + ": " + version);
    }

    private static String topLevelKey(String line) {
        if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t'
                || line.charAt(0) == '#') {
            return null;
        }
        int colon = line.indexOf(':');
        if (colon < 1 || (colon + 1 < line.length() && !Character.isWhitespace(line.charAt(colon + 1)))) {
            return null;
        }
        String key = line.substring(0, colon);
        return key.matches("[A-Za-z0-9_.-]+") ? key : null;
    }

    private static boolean isBlockValue(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String rest = line.substring(colon + 1).trim();
        return rest.isEmpty() || rest.startsWith("|") || rest.startsWith(">");
    }

    private static void write(Path file, String output) throws IOException {
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "messages-", ".tmp");
        try {
            Files.write(temporary, output.getBytes(StandardCharsets.UTF_8));
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

    static final class Result {
        final int added;
        final int refreshed;

        private Result(int added, int refreshed) {
            this.added = added;
            this.refreshed = refreshed;
        }
    }
}
