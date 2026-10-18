/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.shared.config.ConfigRefresher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatYamlReaderTest {
    @TempDir
    Path directory;

    @Test
    void readsScalarsQuotesEmptyValuesCommentsAndLastDuplicate() {
        var result = read("""
                # Entire-line comment

                language: en
                language: zh
                empty:
                quoted-empty: ''
                single: ' it''s # literal: text '
                double: "a \\"quote\\" \\\\ path"
                hash: literal # not an inline comment
                hash-start: #also literal
                  # Indented comments are ignored
                number: 42
                boolean: true
                null: null
                url: https://example.test/path#fragment
                """);
        assertEquals(0, result.unsupportedLine());
        assertEquals(Map.ofEntries(Map.entry("language", "zh"), Map.entry("empty", ""),
                Map.entry("quoted-empty", ""), Map.entry("single", " it's # literal: text "),
                Map.entry("double", "a \"quote\" \\ path"), Map.entry("hash", "literal # not an inline comment"),
                Map.entry("hash-start", "#also literal"), Map.entry("number", "42"),
                Map.entry("boolean", "true"), Map.entry("null", "null"),
                Map.entry("url", "https://example.test/path#fragment")), result.values());
    }

    @Test
    void readsBlockAndFlowListsWithQuotedCommasHashesAndEmptyItems() {
        var result = read("""
                empty: []
                spaced-empty: [  ]
                flow: [first, 'with,comma', "with # hash", '', last,]
                block:
                  - first
                  # List comment

                  - 'with: colon'
                  - #literal
                  -
                language: zh
                """);
        assertEquals(0, result.unsupportedLine());
        assertEquals(List.of(), result.values().get("empty"));
        assertEquals(List.of(), result.values().get("spaced-empty"));
        assertEquals(List.of("first", "with,comma", "with # hash", "", "last"), result.values().get("flow"));
        assertEquals(List.of("first", "with: colon", "#literal", ""), result.values().get("block"));
        assertEquals("zh", result.values().get("language"));
    }

    @Test
    void decodesSingleLineDoubleQuotedEscapes() {
        var result = read("value: \"\\x41\\u0042\\U00000043\\t\\/\\_\"\n");
        assertEquals(0, result.unsupportedLine());
        assertEquals("ABC\t/\u00a0", result.values().get("value"));
    }

    @ParameterizedTest
    @MethodSource("unsupportedLines")
    void stopsAtFirstUnsupportedLineAndRetainsEarlierTopLevelValues(String unsupported) {
        var result = read("language: zh\nallowed-proxy-ids: trusted\n" + unsupported + "\nlanguage: en\n");
        assertEquals(3, result.unsupportedLine());
        assertEquals("zh", result.values().get("language"));
        assertEquals("trusted", result.values().get("allowed-proxy-ids"));
    }

    static Stream<String> unsupportedLines() {
        return Stream.of("  nested: value", "value: &anchor text", "value: *anchor", "value: !!str text",
                "value: !custom text", "value: {nested: value}", "value: |", "value: >-",
                "value: 'unterminated", "value: \"unterminated", "value: 'text' trailing",
                "value: [one, [two]]", "value: [one, {two: three}]", "value: [one,,two]",
                "value: [one", "value: ['one' 'two']", "value: [one, &anchor two]",
                "value: \"bad\\qescape\"", "value: \"multiline\\nscalar\"", "value: \"\\uZZZZ\"",
                "value: \"\\UFFFFFFFF\"", "value: \"\\x+1\"", "value: \"\\uD800\"",
                "value: \"\\x00\"", "value: plain\u0000control", "\tvalue: indented", "- orphan", "---", "%YAML 1.2",
                "? complex-key", "'quoted-key': value", "value:no-separator");
    }

    @Test
    void retainsEarlierListItemsButRejectsNestedOrInconsistentlyIndentedItems() {
        for (String unsupported : List.of("    - nested", "  - nested: mapping", "  - &anchor text")) {
            var result = read("allowed-proxy-ids:\n  - trusted\n" + unsupported + "\nlanguage: zh\n");
            assertEquals(3, result.unsupportedLine());
            assertEquals(List.of("trusted"), result.values().get("allowed-proxy-ids"));
            assertFalse(result.values().containsKey("language"));
        }
    }

    @Test
    void duplicateListsReplaceRatherThanAppendAndScalarCannotOwnAList() {
        var result = read("value:\n  - old\nvalue:\n  - new\nvalue: scalar\n  - orphan\n");
        assertEquals(6, result.unsupportedLine());
        assertEquals("scalar", result.values().get("value"));
        assertEquals(List.of("new"), read("value: [old]\nvalue:\n  - new\n").values().get("value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a:b", "a#b", " leading", "trailing ", " both ", "it's: #quoted",
            "[brackets]", "one,two", "true", "null", "42", "", "#first", "&literal", "a\tvalue"})
    void sharedScalarRenderingRoundTripsAndIsByteIdempotent(String value) throws Exception {
        Path file = directory.resolve("round-trip.yml");
        var template = List.of("# Template comment", "value: default");
        ConfigRefresher.refresh(file, template, Map.of("value", value));
        byte[] first = Files.readAllBytes(file);
        String output = Files.readString(file);
        assertTrue(output.startsWith("# Template comment\nvalue: '"));
        var result = FlatYamlReader.read(Files.readAllLines(file));
        assertEquals(0, result.unsupportedLine());
        assertEquals(value, result.values().get("value"));
        ConfigRefresher.refresh(file, template, result.values());
        assertArrayEquals(first, Files.readAllBytes(file));
    }

    @Test
    void sharedListRenderingProducesReadableYamlAndIsByteIdempotent() throws Exception {
        Path file = directory.resolve("list.yml");
        var template = List.of("# List comment", "values: []");
        var items = List.of("plain", "with:colon", "hash#literal", " space ", "it's", "");
        ConfigRefresher.refresh(file, template, Map.of("values", items));
        byte[] first = Files.readAllBytes(file);
        var result = FlatYamlReader.read(Files.readAllLines(file));
        assertEquals(0, result.unsupportedLine());
        assertEquals(items, result.values().get("values"));
        ConfigRefresher.refresh(file, template, result.values());
        assertArrayEquals(first, Files.readAllBytes(file));
    }

    private static FlatYamlReader.Result read(String yaml) {
        return FlatYamlReader.read(yaml.lines().toList());
    }
}
