/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.md_5.bungee.config.Configuration;

/**
 * Restores config.yml from the bundled template while preserving user values.
 * Comments, section headers, and key ordering come from the template;
 * values come from the user's existing config.
 */
public final class ConfigRefresher {

    /**
     * Keys that only make sense when {@code driver: 'mysql'}.
     * In sqlite mode these stay commented out even if the user has values for them.
     */
    private static final Set<String> MYSQL_KEYS = new HashSet<>(Arrays.asList(
            "host", "port", "username", "password", "useSSL"
    ));

    private ConfigRefresher() {
        // utility class
    }

    /**
     * Rewrite config.yml using the template structure and user's values.
     *
     * <p>After this call the on-disk file has canonical comments and key order
     * from the bundled template, but every value is taken from the user's
     * existing config. Keys that only exist in the user's config (not in the
     * template) are appended at the end.</p>
     *
     * @param classLoader to load the template resource from the JAR
     * @param configPath  path to the user's config.yml on disk
     * @param userConfig  the parsed user Configuration (with all values)
     * @param dbMode      {@code "sqlite"} or {@code "mysql"} — controls which
     *                    database section is uncommented
     * @throws IOException if reading the template or writing the file fails
     */
    public static void refresh(ClassLoader classLoader, Path configPath,
                               Configuration userConfig, String dbMode)
            throws IOException {
        // 1. Flatten user config into a dotted-key map
        Map<String, Object> userValues = new LinkedHashMap<>();
        flattenConfig(userConfig, "", userValues);

        // 2. Read template as raw text lines
        List<String> templateLines = readResourceLines(classLoader, "config.yml");
        if (templateLines == null) {
            return;
        }

        // Pre-scan: collect all active (non-commented) keys in the template.
        // This prevents tryUncomment from uncommenting a key that already has
        // an active line (e.g. "#ip-addresses:" followed by "ip-addresses: []").
        Set<String> activeKeys = collectActiveKeys(templateLines);

        // 3. Walk template line by line, substituting user values
        List<String> output = new ArrayList<>();
        List<String> sectionPath = new ArrayList<>();
        List<Integer> indentStack = new ArrayList<>();
        Set<String> consumedKeys = new HashSet<>();

        for (String line : templateLines) {
            String trimmed = line.trim();

            // Keep comments and blank lines as-is
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                // In sqlite mode, MySQL-specific keys must stay commented
                if ("sqlite".equals(dbMode) && isMysqlKey(trimmed)) {
                    output.add(line);
                    continue;
                }
                // Check if this is a commented-out config key (#key: value)
                String uncommented = tryUncomment(trimmed, sectionPath,
                        userValues, consumedKeys, activeKeys);
                if (uncommented != null) {
                    String pad = line.substring(0, getIndentLevel(line));
                    output.add(pad + uncommented);
                } else {
                    output.add(line);
                }
                continue;
            }

            int indent = getIndentLevel(line);

            // Pop sections when indentation decreases to (or below) their level
            while (!indentStack.isEmpty()
                    && indent <= indentStack.get(indentStack.size() - 1)) {
                indentStack.remove(indentStack.size() - 1);
                sectionPath.remove(sectionPath.size() - 1);
            }

            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0) {
                output.add(line);
                continue;
            }

            String key = trimmed.substring(0, colonIdx).trim();
            String rest = trimmed.substring(colonIdx + 1).trim();

            // Build the full dotted key path
            String fullKey = sectionPath.isEmpty() ? key
                    : String.join(".", sectionPath) + "." + key;

            consumedKeys.add(fullKey);

            if (rest.isEmpty()) {
                // No value on the same line — section header or list key
                Object userVal = userValues.get(fullKey);

                if (userVal instanceof List) {
                    // List key (e.g. "proxies:" with list items below)
                    output.add(line);
                    List<?> list = (List<?>) userVal;
                    if (!list.isEmpty()) {
                        String pad = spaces(indent + 2);
                        for (Object item : list) {
                            output.add(pad + "- " + toScalarYaml(item));
                        }
                    }
                    // Do NOT push to section stack — it's a list, not a map
                } else {
                    // Section header (e.g. "anti-bot:")
                    output.add(line);
                    sectionPath.add(key);
                    indentStack.add(indent);
                }
            } else if (rest.equals("[]")) {
                // Inline empty list (e.g. "trusted-ips: []")
                Object userVal = userValues.get(fullKey);
                if (userVal instanceof List && !((List<?>) userVal).isEmpty()) {
                    // Replace [] with expanded list items
                    int bracketIdx = line.indexOf("[]");
                    output.add(line.substring(0, bracketIdx));
                    String pad = spaces(indent + 2);
                    for (Object item : (List<?>) userVal) {
                        output.add(pad + "- " + toScalarYaml(item));
                    }
                } else {
                    output.add(line);
                }
            } else {
                // Scalar value on the same line
                Object userVal = userValues.get(fullKey);
                if (userVal != null) {
                    String newYaml = toScalarYaml(userVal);
                    // Preserve the template's quoting style
                    boolean templateQuoted = rest.startsWith("'") || rest.startsWith("\"");
                    if (templateQuoted && !isQuoted(newYaml)) {
                        newYaml = "'" + newYaml.replace("'", "''") + "'";
                    }
                    int lineColonIdx = line.indexOf(':');
                    output.add(line.substring(0, lineColonIdx + 1) + " " + newYaml);
                } else {
                    // User has no value for this key — keep template default
                    output.add(line);
                }
            }
        }

        // 4. Append user-only top-level keys and write back
        appendRemainingAndWrite(userValues, consumedKeys, output, configPath);
    }

    /**
     * Append user-only top-level keys that don't exist in the template,
     * then write the output lines back to the config file.
     *
     * @param userValues   flattened user config values
     * @param consumedKeys keys already matched by template lines
     * @param output       the output lines to write
     * @param configPath   path to the config file
     * @throws IOException if writing the file fails
     */
    private static void appendRemainingAndWrite(Map<String, Object> userValues,
                                                Set<String> consumedKeys,
                                                List<String> output,
                                                Path configPath)
            throws IOException {
        List<String> remaining = new ArrayList<>();
        for (String k : userValues.keySet()) {
            if (!consumedKeys.contains(k) && !k.contains(".")) {
                remaining.add(k);
            }
        }
        if (!remaining.isEmpty()) {
            output.add("");
            output.add("# ========================================");
            output.add("# User-added keys (not in bundled template)");
            output.add("# ========================================");
            for (String k : remaining) {
                Object val = userValues.get(k);
                output.add(k + ": " + toScalarYaml(val));
            }
        }

        // Write back (UTF-8, LF line endings)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < output.size(); i++) {
            sb.append(output.get(i));
            if (i < output.size() - 1) {
                sb.append('\n');
            }
        }
        Files.write(configPath, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Try to uncomment a commented-out config key.
     * If the trimmed line is {@code #key: value} and the user has a value for
     * that key (and it was not already consumed by an active line), return the
     * uncommented key-value string. Otherwise return null.
     *
     * @param trimmed      the trimmed comment line (starts with #)
     * @param sectionPath  the current section path
     * @param userValues   the flattened user config values
     * @param consumedKeys keys already matched by active template lines
     * @param activeKeys   all active (non-commented) keys in the template
     * @return the uncommented line, or null to keep the comment as-is
     */
    private static String tryUncomment(String trimmed, List<String> sectionPath,
                                       Map<String, Object> userValues,
                                       Set<String> consumedKeys,
                                       Set<String> activeKeys) {
        if (!trimmed.startsWith("#")) {
            return null;
        }
        String uncommented = trimmed.substring(1).trim();
        int cIdx = uncommented.indexOf(':');
        if (cIdx <= 0) {
            return null;
        }
        String potKey = uncommented.substring(0, cIdx).trim();
        // Must look like a YAML key: non-empty, no spaces
        if (potKey.isEmpty() || potKey.contains(" ")) {
            return null;
        }
        String fk = sectionPath.isEmpty() ? potKey
                : String.join(".", sectionPath) + "." + potKey;
        // Skip if this key already has an active line in the template
        if (activeKeys.contains(fk)) {
            return null;
        }
        Object uv = userValues.get(fk);
        if (uv == null || consumedKeys.contains(fk)) {
            return null;
        }
        String newYaml = toScalarYaml(uv);
        String restVal = uncommented.substring(cIdx + 1).trim();
        boolean tq = restVal.startsWith("'") || restVal.startsWith("\"");
        if (tq && !isQuoted(newYaml)) {
            newYaml = "'" + newYaml.replace("'", "''") + "'";
        }
        consumedKeys.add(fk);
        return potKey + ": " + newYaml;
    }

    /**
     * Check if a comment line is a MySQL-specific config key.
     *
     * @param trimmed the trimmed comment line (starts with #)
     * @return true if the line is {@code #mysqlKey: ...} where mysqlKey is in MYSQL_KEYS
     */
    private static boolean isMysqlKey(String trimmed) {
        if (!trimmed.startsWith("#")) {
            return false;
        }
        String uncommented = trimmed.substring(1).trim();
        int cIdx = uncommented.indexOf(':');
        if (cIdx <= 0) {
            return false;
        }
        return MYSQL_KEYS.contains(uncommented.substring(0, cIdx).trim());
    }

    // ---- internal helpers ------------------------------------------------

    /**
     * Pre-scan template lines to collect all active (non-commented) keys.
     * Used to prevent tryUncomment from uncommenting a key that already has
     * an active line in the template.
     *
     * @param lines the template lines
     * @return set of dotted key paths for all active keys
     */
    private static Set<String> collectActiveKeys(List<String> lines) {
        Set<String> keys = new HashSet<>();
        List<String> section = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int indent = getIndentLevel(line);
            while (!indents.isEmpty() && indent <= indents.get(indents.size() - 1)) {
                indents.remove(indents.size() - 1);
                section.remove(section.size() - 1);
            }

            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0) {
                continue;
            }

            String key = trimmed.substring(0, colonIdx).trim();
            String rest = trimmed.substring(colonIdx + 1).trim();

            String fullKey = section.isEmpty() ? key
                    : String.join(".", section) + "." + key;
            keys.add(fullKey);

            // Track section headers (no value on same line, not a list key)
            if (rest.isEmpty()) {
                section.add(key);
                indents.add(indent);
            }
        }
        return keys;
    }

    /**
     * Recursively flatten a BungeeCord Configuration into dotted-key map.
     *
     * @param config the configuration section to flatten
     * @param prefix the dotted prefix for nested keys
     * @param result the map to populate with flattened key-value pairs
     */
    private static void flattenConfig(Configuration config, String prefix,
                                      Map<String, Object> result) {
        for (String key : config.getKeys()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = config.get(key);
            if (value instanceof Configuration) {
                flattenConfig((Configuration) value, fullKey, result);
            } else if (value != null) {
                result.put(fullKey, value);
            }
        }
    }

    private static List<String> readResourceLines(ClassLoader cl, String resource)
            throws IOException {
        try (InputStream is = cl.getResourceAsStream(resource)) {
            if (is == null) {
                return null;
            }
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }
    }

    private static int getIndentLevel(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static String spaces(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Convert a Java value to a single-line YAML scalar.
     * Lists are handled separately in the main loop.
     *
     * @param value the Java value to convert
     * @return the YAML scalar string
     */
    private static String toScalarYaml(Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof String) {
            String s = (String) value;
            if (needsQuoting(s)) {
                return "'" + s.replace("'", "''") + "'";
            }
            return s;
        }
        return String.valueOf(value);
    }

    /**
     * Return true if the string must be quoted in YAML.
     *
     * @param s the string to check
     * @return true if quoting is required
     */
    private static boolean needsQuoting(String s) {
        if (s.isEmpty()) {
            return true;
        }
        // YAML reserved starting characters
        char first = s.charAt(0);
        if (first == '?' || first == ':' || first == ',' || first == '-'
                || first == '[' || first == ']' || first == '{' || first == '}'
                || first == '#' || first == '&' || first == '*' || first == '!'
                || first == '|' || first == '>' || first == '\'' || first == '"'
                || first == '%' || first == '@' || first == '`') {
            return true;
        }
        // Characters that are significant anywhere in the value
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':' || c == '#' || c == '{' || c == '}'
                    || c == '[' || c == ']' || c == ',') {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the string is already wrapped in quotes.
     *
     * @param s the string to check
     * @return true if the string is quoted
     */
    private static boolean isQuoted(String s) {
        return (s.length() >= 2)
                && ((s.startsWith("'") && s.endsWith("'"))
                    || (s.startsWith("\"") && s.endsWith("\"")));
    }
}
