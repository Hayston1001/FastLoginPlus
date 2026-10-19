/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.model.auth;

import com.github.games647.craftapi.model.skin.SkinProperty;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Premium server join verification response.
 */
public class Verification {

    private final UUID id;
    private final String name;
    private final SkinProperty[] properties;

    /**
     * @param id premium UUID
     * @param name case-sensitive player name
     * @param properties profile properties like skin data
     */
    public Verification(UUID id, String name, SkinProperty[] properties) {
        this.id = id;
        this.name = name;
        this.properties = properties;
    }

    /**
     * @return premium UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return case-sensitive player name
     */
    public String getName() {
        return name;
    }

    /**
     * @return additional properties like Skin data
     */
    public SkinProperty[] getProperties() {
        return Arrays.copyOf(properties, properties.length);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Verification) {
            Verification that = (Verification) other;
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
