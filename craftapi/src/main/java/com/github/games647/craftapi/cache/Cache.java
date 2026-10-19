/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
