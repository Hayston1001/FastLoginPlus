/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamePredicateTest {

    private NamePredicate predicate;

    @BeforeEach
    void setUp() {
        predicate = new NamePredicate();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdwef",
            "zezvw"
    })
    void testValidSimple(String name) {
        assertTrue(predicate.test(name));
    }

    @Test
    void testValidUnderscore() {
        assertTrue(predicate.test("rashomon_"));
    }

    @Test
    void testDifferentCasing() {
        assertTrue(predicate.test("FoggyMonster"));
    }

    @Test
    void testNumbers() {
        assertTrue(predicate.test("F0ggyMonst3r"));
    }

    @Test
    void testNull() {
        assertThrows(NullPointerException.class,
                () -> predicate.test(null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12",
            "1234567890123456"
    })
    void testValidLength(String name) {
        assertTrue(predicate.test(name));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = "12345678901234567")
    void testInvalidLength(String name) {
        assertFalse(predicate.test(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc$",
            "fdwadaä",
            //dot
            //https://sessionserver.mojang.com/session/minecraft/profile/97d8ecc8607b4760b1c7fb5792c45d01
            "Mr.Denis",
            // dash
            //https://sessionserver.mojang.com/session/minecraft/profile/cca4953341074ef5a196a6e67104277d
            "football-flo",
            // characters that lie between 'Z' and 'a' - an [a-zA-z] range would let them through
            "rashomon[",
            "rasho\\mon",
            "rashomon]",
            "rasho^mon",
            "rasho`mon"
    })
    void testInvalidCharacters(String name) {
        assertFalse(predicate.test(name));
    }
}
