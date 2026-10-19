/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Base64;

public class Base64Adapter extends TypeAdapter<byte[]> {

    @Override
    public void write(JsonWriter out, byte[] value) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(value);
        out.value(encoded);
    }

    @Override
    public byte[] read(JsonReader in) throws IOException {
        String encoded = in.nextString();
        return Base64.getDecoder().decode(encoded);
    }
}
