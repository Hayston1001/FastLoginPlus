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
            "football-flo"
    })
    void testInvalidCharacters(String name) {
        assertFalse(predicate.test(name));
    }
}
