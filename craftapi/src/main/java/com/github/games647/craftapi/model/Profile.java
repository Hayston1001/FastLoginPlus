/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Mojang game profile.
 */
public class Profile {

    protected UUID id;
    protected String name;

    /**
     * Mojang premium game profile
     *
     * @param id premium UUID
     * @param name case-sensitive player name
     */
    public Profile(UUID id, String name) {
        this.id = id;
        this.name = name;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Profile) {
            Profile that = (Profile) other;
            return Objects.equals(id, that.id);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "id=" + id
                + ", name='" + name + '\''
                + '}';
    }
}
