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
package com.github.games647.craftapi.model.skin;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * All texture data that can be accessed from a Mojang request.a
 */
public class Textures {

    public static final String KEY = "textures";

    private final UUID id;
    private final String name;
    private final SkinProperty[] properties;

    public Textures(UUID id, String name, SkinProperty[] properties) {
        this.id = id;
        this.name = name;
        this.properties = properties;
    }

    /**
     * @return premium UUID of the owner
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return case-sensitive player name of the owner
     */
    public String getName() {
        return name;
    }

    /**
     * @return copy of the skin properties
     */
    public SkinProperty[] getProperties() {
        return Arrays.copyOf(properties, properties.length);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Textures) {
            Textures that = (Textures) other;
            return Objects.equals(id, that.id)
                    && Objects.equals(name, that.name)
                    && Arrays.equals(properties, that.properties);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name);
        result = 31 * result + Arrays.hashCode(properties);
        return result;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "id=" + id
                + ", name='" + name + '\''
                + ", properties=" + Arrays.toString(properties)
                + '}';
    }
}
