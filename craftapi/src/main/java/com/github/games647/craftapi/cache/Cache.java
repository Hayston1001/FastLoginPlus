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
package com.github.games647.craftapi.cache;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.UUID;

/**
 * Cache for the skins and profiles.
 */
public interface Cache {

    /**
     * Manually adds a profile cache entry.
     *
     * @param profile to cached profile
     */
    void add(Profile profile);

    /**
     * Manually adds a skin cache entry.
     *
     * @param uniqueId UUID associated to this skin
     * @param property skin
     */
    void addSkin(UUID uniqueId, SkinProperty property);

    /**
     * Invalidate profile cache entry
     *
     * @param profile profile that should be removed from cache
     */
    void remove(Profile profile);

    /**
     * Invalidate skin from cache.
     *
     * @param uniqueId skin owner id
     */
    void removeSkin(UUID uniqueId);

    /**
     * Invalidate all cache entries.
     */
    void clear();

    /**
     * Get profile by case-insensitive player name
     *
     * @param playerName case-insensitive player name
     * @return profile or empty if not present in cache
     */
    Optional<Profile> getByName(String playerName);

    /**
     * Get profile by premium UUID.
     *
     * @param uniqueId premium UUID
     * @return profile or empty if not present in cache
     */
    Optional<Profile> getById(UUID uniqueId);

    /**
     * Get the skin from cache.
     *
     * @param uniqueId owner id
     * @return the skin or empty if not present in cache
     */
    Optional<SkinProperty> getSkin(UUID uniqueId);

    /**
     * @return immutable list of all currently cached profiles
     */
    ImmutableSet<Profile> getCachedProfiles();

    /**
     * @return immutable list of all currently cached skins
     */
    ImmutableSet<SkinProperty> getCachedSkins();

}
