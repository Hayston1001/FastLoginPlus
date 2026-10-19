/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The deliberately flat, string-only YAML subset used by the local settings. */
final class FlatYamlReader {
    private FlatYamlReader() {
    }

    /** A zero line number means the entire input was supported. */
    record Result(Map<String, Object> values, int unsupportedLine) {
    }

    static Result read(List<String> lines) {
        return read(lines, false);
    }

    /**
     * Parses the flat YAML subset used by messages.
     *
     * @param lines input lines
     * @return parsed values and the first unsupported line, or zero
     */
    static Result readMessages(List<String> lines) {
        return read(lines, true);
    }

    private static Result read(List<String> lines, boolean allowControlCharacters) {
        Map<String, Object> values = new LinkedHashMap<>();
        String listKey = null;
        List<String> blockItems = null;
        int listIndent = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            try {
                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') {
                    indent++;
                }
                if (Character.isWhitespace(line.charAt(indent))) {
                    throw new IllegalArgumentException("Tabs in indentation");
                }
                if (indent > 0) {
                    if (listKey == null || !(text.equals("-") || text.startsWith("- "))
                            || (listIndent >= 0 && listIndent != indent)) {
                        throw new IllegalArgumentException("Nested or orphaned content");
                    }
                    String item = scalar(text.substring(1).strip(), allowControlCharacters);
                    if (blockItems == null) {
                        blockItems = new ArrayList<>();
                        values.put(listKey, blockItems);
                        listIndent = indent;
                    }
                    blockItems.add(item);
                } else {
                    int colon = text.indexOf(':');
                    if (colon < 1 || !text.substring(0, colon).matches("[A-Za-z0-9_.-]+")
                            || (colon + 1 < text.length() && !Character.isWhitespace(text.charAt(colon + 1)))) {
                        throw new IllegalArgumentException("Not a top-level key");
                    }
                    String key = text.substring(0, colon);
                    String raw = text.substring(colon + 1).strip();
                    Object value = raw.startsWith("[") ? flowList(raw, allowControlCharacters)
                            : scalar(raw, allowControlCharacters);
                    values.put(key, value);
                    listKey = raw.isEmpty() ? key : null;
                    blockItems = null;
                    listIndent = -1;
                }
            } catch (IllegalArgumentException unsupported) {
                return new Result(values, index + 1);
            }
        }
        return new Result(values, 0);
    }

    private static String scalar(String raw, boolean allowControlCharacters) {
        if (raw.isEmpty()) {
            return "";
        }
        char first = raw.charAt(0);
        if (first == '\'' || first == '"') {
            Token token = quoted(raw, 0, allowControlCharacters);
            if (token.end() != raw.length()) {
                throw new IllegalArgumentException("Content after a quoted scalar");
            }
            return token.value();
        }
        if ("&*!|>{}[]%@`".indexOf(first) >= 0 || raw.equals("-") || raw.startsWith("- ")
                || raw.equals("?") || raw.startsWith("? ") || raw.matches(".*:\\s.*") || raw.endsWith(":")) {
            throw new IllegalArgumentException("Not a plain scalar");
        }
        // Hashes are literal inside values, not inline comments in this subset.
        return singleLine(raw, allowControlCharacters);
    }

    private static List<String> flowList(String raw, boolean allowControlCharacters) {
        if (!raw.endsWith("]")) {
            throw new IllegalArgumentException("Unterminated list");
        }
        List<String> items = new ArrayList<>();
        int end = raw.length() - 1;
        int cursor = 1;
        while (cursor < end) {
            while (cursor < end && Character.isWhitespace(raw.charAt(cursor))) {
                cursor++;
            }
            if (cursor == end) {
                break;
            }
            char first = raw.charAt(cursor);
            if (first == '\'' || first == '"') {
                Token token = quoted(raw, cursor, allowControlCharacters);
                if (token.end() > end) {
                    throw new IllegalArgumentException("Unterminated list item");
                }
                items.add(token.value());
                cursor = token.end();
            } else {
                int comma = raw.indexOf(',', cursor);
                int stop = comma < 0 ? end : comma;
                String item = raw.substring(cursor, stop).strip();
                if (item.isEmpty() || item.indexOf('[') >= 0 || item.indexOf(']') >= 0
                        || item.indexOf('{') >= 0 || item.indexOf('}') >= 0) {
                    throw new IllegalArgumentException("Nested or empty list item");
                }
                items.add(scalar(item, allowControlCharacters));
                cursor = stop;
            }
            while (cursor < end && Character.isWhitespace(raw.charAt(cursor))) {
                cursor++;
            }
            if (cursor < end && raw.charAt(cursor++) != ',') {
                throw new IllegalArgumentException("Missing list separator");
            }
        }
        return items;
    }

    private record Token(String value, int end) {
    }

    private static Token quoted(String raw, int start, boolean allowControlCharacters) {
        char quote = raw.charAt(start);
        StringBuilder value = new StringBuilder();
        int cursor = start + 1;
        while (cursor < raw.length()) {
            char next = raw.charAt(cursor++);
            if (next == quote) {
                if (quote == '\'' && cursor < raw.length() && raw.charAt(cursor) == '\'') {
                    value.append('\'');
                    cursor++;
                } else {
                    return new Token(singleLine(value.toString(), allowControlCharacters), cursor);
                }
            } else if (quote == '"' && next == '\\') {
                if (cursor == raw.length()) {
                    throw new IllegalArgumentException("Unterminated escape");
                }
                char escape = raw.charAt(cursor++);
                int digits = switch (escape) {
                    case 'x' -> 2;
                    case 'u' -> 4;
                    case 'U' -> 8;
                    default -> 0;
                };
                if (digits > 0) {
                    if (cursor + digits > raw.length()) {
                        throw new IllegalArgumentException("Incomplete Unicode escape");
                    }
                    String hex = raw.substring(cursor, cursor + digits);
                    if (!hex.matches("[0-9a-fA-F]+")) {
                        throw new IllegalArgumentException("Invalid Unicode escape");
                    }
                    int codePoint = Integer.parseInt(hex, 16);
                    value.appendCodePoint(codePoint);
                    cursor += digits;
                } else {
                    value.append(escaped(escape));
                }
            } else {
                value.append(next);
            }
        }
        throw new IllegalArgumentException("Unterminated quoted scalar");
    }

    private static char escaped(char escape) {
        return switch (escape) {
            case '0' -> '\0';
            case 'a' -> '\u0007';
            case 'b' -> '\b';
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'v' -> '\u000b';
            case 'f' -> '\f';
            case 'r' -> '\r';
            case 'e' -> '\u001b';
            case 'N' -> '\u0085';
            case '_' -> '\u00a0';
            case 'L' -> '\u2028';
            case 'P' -> '\u2029';
            case ' ', '"', '/', '\\' -> escape;
            default -> throw new IllegalArgumentException("Unsupported escape");
        };
    }

    static String singleLine(String value) {
        return singleLine(value, false);
    }

    private static String singleLine(String value, boolean allowControlCharacters) {
        // The shared writer uses physical lines for embedded CR/LF; that shape is not flat.
        if (!allowControlCharacters && value.codePoints().anyMatch(c ->
                (c < 0x20 && c != '\t') || (c >= 0x7f && c <= 0x9f && c != 0x85)
                        || (c >= 0xd800 && c <= 0xdfff) || c == 0xfffe || c == 0xffff)) {
            throw new IllegalArgumentException("Not a single-line YAML string");
        }
        return value;
    }
}
