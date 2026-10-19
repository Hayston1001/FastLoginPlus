/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstantAdapterTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .create();
    }

    @Test
    void testReading() {
        Instant time = gson.fromJson("1519487280972", Instant.class);
        assertEquals(time, Instant.ofEpochMilli(1519487280972L));
    }

    @Test
    void testWriting() {
        String json = gson.toJson(Instant.ofEpochMilli(1519487280972L));
        assertEquals(json, "1519487280972");
    }
}
