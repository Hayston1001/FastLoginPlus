/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Name change history
 */
public class NameHistory {

    private final String username;
    private final Instant changedToAt;

    /**
     * History entry
     *
     * @param username name at the {@link #changedToAt} time
     * @param changedToAt when the player changed it
     */
    public NameHistory(String username, Instant changedToAt) {
        this.username = username;
        this.changedToAt = changedToAt;
    }

    /**
     * Represents the latest/newest history entry
     *
     * @param username current active player name
     */
    public NameHistory(String username) {
        this(username, null);
    }

    /**
     * @return name at {@link #changedToAt} time
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return when did the name was changed or empty if it's the current name
     */
    public Optional<Instant> getChangedToAt() {
        return Optional.ofNullable(changedToAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof NameHistory) {
            NameHistory that = (NameHistory) other;
            return Objects.equals(username, that.username)
                    && Objects.equals(changedToAt, that.changedToAt);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, changedToAt);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "username='" + username + '\''
                + ", changedToAt=" + changedToAt
                + '}';
    }
}
