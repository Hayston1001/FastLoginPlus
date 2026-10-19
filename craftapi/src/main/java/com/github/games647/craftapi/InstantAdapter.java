/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

/**
 * Converts the milliseconds into Java 8's Instant objects.
 */
public class InstantAdapter extends TypeAdapter<Instant> {

    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        out.value(value.toEpochMilli());
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        return Instant.ofEpochMilli(in.nextLong());
    }
}
