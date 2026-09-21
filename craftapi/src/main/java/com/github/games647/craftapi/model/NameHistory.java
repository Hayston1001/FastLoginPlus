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
