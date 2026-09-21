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
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * In memory cache for the skins and profiles.
 */
public class MemoryCache implements Cache {

    private static final int DEFAULT_UUID_EXPIRE = 16_384;
    private static final int DEFAULT_UUID_SIZE = 60;

    private static final int DEFAULT_SKIN_EXPIRE = 5;
    private static final int DEFAULT_SKIN_SIZE = 0;

    private final ConcurrentMap<UUID, Profile> uuidToProfileCache;
    private final ConcurrentMap<String, Profile> nameToProfileCache;

    private final ConcurrentMap<UUID, SkinProperty> skinCache;

    /**
     * Creates a new memory cache with custom configuration options.
     *
     * @param uuidExpire uuid cache expiration time 0 to disable
     * @param uuidSize uuid max cache size &le; 0 to disable
     * @param skinExpire skin cache expiration time 0 to disable
     * @param skinSize skin max cache size &le; 0 to disable
     */
    public MemoryCache(Duration uuidExpire, int uuidSize, Duration skinExpire, int skinSize) {
        uuidToProfileCache = buildCache(uuidExpire.getSeconds(), uuidSize);
        nameToProfileCache = buildCache(uuidExpire.getSeconds(), uuidSize);

        skinCache = buildCache(skinExpire.getSeconds(), skinSize);
    }

    /**
     * Creates a new skin cache with default parameters
     */
    public MemoryCache() {
        this(Duration.ofMinutes(DEFAULT_UUID_EXPIRE), DEFAULT_UUID_SIZE,
                Duration.ofMinutes(DEFAULT_SKIN_EXPIRE), DEFAULT_SKIN_SIZE);
    }

    @Override
    public void add(Profile profile) {
        uuidToProfileCache.put(profile.getId(), profile);
        nameToProfileCache.put(profile.getName().toLowerCase(), profile);
    }

    @Override
    public void addSkin(UUID uniqueId, SkinProperty property) {
        skinCache.put(uniqueId, property);
    }

    @Override
    public void remove(Profile profile) {
        uuidToProfileCache.remove(profile.getId(), profile);
        nameToProfileCache.remove(profile.getName().toLowerCase(), profile);
    }

    @Override
    public void removeSkin(UUID uniqueId) {
        skinCache.remove(uniqueId);
    }

    @Override
    public void clear() {
        uuidToProfileCache.clear();
        nameToProfileCache.clear();
        skinCache.clear();
    }

    @Override
    public Optional<Profile> getByName(String playerName) {
        return Optional.ofNullable(nameToProfileCache.get(playerName.toLowerCase()));
    }

    @Override
    public Optional<Profile> getById(UUID uniqueId) {
        return Optional.ofNullable(uuidToProfileCache.get(uniqueId));
    }

    @Override
    public Optional<SkinProperty> getSkin(UUID uniqueId) {
        return Optional.ofNullable(skinCache.get(uniqueId));
    }

    @Override
    public ImmutableSet<Profile> getCachedProfiles() {
        return ImmutableSet.copyOf(uuidToProfileCache.values());
    }

    @Override
    public ImmutableSet<SkinProperty> getCachedSkins() {
        return ImmutableSet.copyOf(skinCache.values());
    }

    private <K, V> ConcurrentMap<K, V> buildCache(long expireAfterWrite, int maxSize) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();

        if (expireAfterWrite > 0) {
            builder.expireAfterWrite(expireAfterWrite, TimeUnit.SECONDS);
        }

        if (maxSize > 0) {
            builder.maximumSize(maxSize);
        }

        return builder.<K, V>build().asMap();
    }
}
