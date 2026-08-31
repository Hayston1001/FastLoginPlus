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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.md_5.bungee.config.Configuration;

/**
 * Restores config.yml from the bundled template while preserving user values.
 * Comments, section headers, and key ordering come from the template;
 * values come from the user's existing config.
 */
public final class ConfigRefresher {

    private ConfigRefresher() {
        // utility class
    }

    /**
     * Rewrite config.yml using the template structure and user's values.
     *
     * <p>After this call the on-disk file has canonical comments and key order
     * from the bundled template, but every value is taken from the user's
     * existing config.</p>
     *
     * @param classLoader  to load the template resource from the JAR
     * @param configPath   path to the user's config.yml on disk
     * @param userConfig   the parsed user Configuration (with all values)
     * @param templateName bundled template resource name
     *                     (e.g. "config.yml", "config-proxy.yml")
     * @throws IOException if reading the template or writing the file fails
     */
    public static void refresh(ClassLoader classLoader, Path configPath,
                               Configuration userConfig, String templateName)
            throws IOException {
        // 1. Flatten user config into a dotted-key map
        Map<String, Object> userValues = new LinkedHashMap<>();
        flattenConfig(userConfig, "", userValues);

        // 2. Read template as raw text lines
        List<String> templateLines = readResourceLines(classLoader, templateName);
        if (templateLines == null) {
            return;
        }

        // 3. Walk template line by line, substituting user values
        List<String> output = new ArrayList<>();
        List<String> sectionPath = new ArrayList<>();
        List<Integer> indentStack = new ArrayList<>();

        for (int lineIndex = 0; lineIndex < templateLines.size(); lineIndex++) {
            String line = templateLines.get(lineIndex);
            String trimmed = line.trim();

            // Keep comments and blank lines as-is
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                output.add(line);
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

            if (rest.isEmpty()) {
                // No value on the same line — section header, list key, or
                // a scalar whose value sits on its own line (e.g.
                // "ServerRSAPublicKeyFile:")
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
                } else if (userVal != null && !(userVal instanceof Configuration)
                        && !templateHasChildren(templateLines, lineIndex, indent)) {
                    // Scalar key written without a value in the template —
                    // preserve the user's value on the key line.  If the template
                    // treats this key as a section header (children follow), keep
                    // the template structure instead: replacing the header with a
                    // scalar would orphan the child keys and produce invalid YAML
                    // (0.5.0/F029).
                    output.add(line.substring(0, line.indexOf(':') + 1)
                            + " " + toScalarYaml(userVal));
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

        // 4. Write back
        writeOutput(output, configPath);
    }

    /**
     * Write the output lines back to the config file.
     *
     * @param output     the output lines to write
     * @param configPath path to the config file
     * @throws IOException if writing the file fails
     */
    private static void writeOutput(List<String> output, Path configPath)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < output.size(); i++) {
            sb.append(output.get(i));
            if (i < output.size() - 1) {
                sb.append('\n');
            }
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        // Atomic rewrite (0.5.0/F026): a crash mid-write must never truncate
        // the user's config — write to a temp file in the same directory and
        // move it over the target (falls back to a non-atomic replace on
        // filesystems without atomic move support).
        Path parent = configPath.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(parent != null ? parent
                : configPath.toAbsolutePath(), "flp-config", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, configPath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ---- internal helpers ------------------------------------------------

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

    /**
     * Check whether the template line at the given index opens a section with
     * child entries (a deeper-indented content line follows).
     *
     * @param templateLines the template lines
     * @param lineIndex     index of the current line
     * @param indent        indent level of the current line
     * @return true if the next content line belongs to this key
     */
    private static boolean templateHasChildren(List<String> templateLines,
                                               int lineIndex, int indent) {
        for (int i = lineIndex + 1; i < templateLines.size(); i++) {
            String next = templateLines.get(i);
            String nextTrimmed = next.trim();
            if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("#")) {
                continue;
            }
            return getIndentLevel(next) > indent;
        }
        return false;
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
        // leading/trailing whitespace changes the scalar
        if (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' ') {
            return true;
        }
        // line breaks / tabs would break the single-line scalar (0.5.0/F030)
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                return true;
            }
        }
        // YAML 1.1 resolver ambiguity: values that would parse as boolean,
        // null or number must be quoted to keep their string type (0.5.0/F030)
        if (isAmbiguousScalar(s)) {
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
     * Return true if the string would be resolved as a boolean, null or number
     * by a YAML 1.1 parser (SnakeYAML) even though it is a plain string value.
     *
     * @param s the string to check
     * @return true if the value is a YAML 1.1 ambiguous scalar
     */
    private static boolean isAmbiguousScalar(String s) {
        switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "true":
            case "false":
            case "yes":
            case "no":
            case "on":
            case "off":
            case "y":
            case "n":
            case "null":
            case "~":
                return true;
            default:
                break;
        }
        // integer forms Double.parseDouble rejects: underscore groups, octal,
        // hexadecimal
        if (s.matches("[+-]?[0-9][0-9_]*") || s.matches("0[0-7_]+")
                || s.matches("[+-]?0[xX][0-9a-fA-F_]+")) {
            return true;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
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
