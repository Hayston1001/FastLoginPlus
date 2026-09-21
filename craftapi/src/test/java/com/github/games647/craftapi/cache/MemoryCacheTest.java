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
import com.github.games647.craftapi.model.skin.SkinPropertyTest;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCacheTest {

    private Cache cache;

    @BeforeEach
    void setUp() {
        cache = new MemoryCache(Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 1);
    }

    @Test
    void maxSizeProfile() {
        assertEquals(cache.getCachedProfiles().size(), 0);

        cache.add(new Profile(UUID.randomUUID(), "1"));
        cache.add(new Profile(UUID.randomUUID(), "2"));

        assertEquals(cache.getCachedProfiles().size(), 1);
    }

    @Test
    void maxSizeSkin() {
        assertEquals(cache.getCachedSkins().size(), 0);

        SkinProperty property1 = new SkinProperty(SkinPropertyTest.STEVE_VALUE, SkinPropertyTest.STEVE_SIGNATURE);
        SkinProperty property2 = new SkinProperty(SkinPropertyTest.SLIM_VALUE, SkinPropertyTest.SLIM_SIGNATURE);
        cache.addSkin(UUID.randomUUID(), property1);
        cache.addSkin(UUID.randomUUID(), property2);

        assertEquals(cache.getCachedSkins().size(), 1);
    }

    @Test
    void addSkin() {
        UUID profileId = UUID.randomUUID();

        assertFalse(cache.getSkin(profileId).isPresent());

        SkinProperty property = new SkinProperty(SkinPropertyTest.STEVE_VALUE, SkinPropertyTest.STEVE_SIGNATURE);
        cache.addSkin(profileId, property);

        assertEquals(cache.getSkin(profileId).get(), property);
    }

    @Test
    void addProfile() {
        Profile profile = new Profile(UUID.randomUUID(), "abc");

        assertFalse(cache.getById(profile.getId()).isPresent());
        cache.add(profile);

        assertEquals(cache.getById(profile.getId()).get(), profile);
    }

    @Test
    void profileCaseInsensitive() {
        Profile profile = new Profile(UUID.randomUUID(), "123ABC_abc");
        cache.add(profile);

        //all lower case + all upper case + mixed
        assertAll(
                () -> assertEquals(cache.getByName("123abc_abc").orElse(null), profile),
                () -> assertEquals(cache.getByName("123ABC_ABC").orElse(null), profile),
                () -> assertEquals(cache.getByName("123abc_ABC").orElse(null), profile)
        );
    }

    @Test
    void clear() {
        Profile profile = new Profile(UUID.randomUUID(), "123ABC_abc");
        cache.add(profile);

        SkinProperty property = new SkinProperty(SkinPropertyTest.STEVE_VALUE, SkinPropertyTest.STEVE_SIGNATURE);
        cache.addSkin(profile.getId(), property);

        cache.clear();
        assertAll(
                () -> assertFalse(cache.getByName(profile.getName()).isPresent()),
                () -> assertFalse(cache.getById(profile.getId()).isPresent()),
                () -> assertFalse(cache.getSkin(profile.getId()).isPresent())
        );
    }

    @Test
    void removeProfile() {
        Profile testProfile = new Profile(UUID.randomUUID(), "123ABC_abc");
        cache.add(testProfile);

        cache.remove(new Profile(UUID.randomUUID(), "123"));
        assertAll(
                () -> assertTrue(cache.getById(testProfile.getId()).isPresent()),
                () -> assertTrue(cache.getByName(testProfile.getName()).isPresent())
        );

        cache.remove(testProfile);
        assertAll(
                () -> assertFalse(cache.getById(testProfile.getId()).isPresent()),
                () -> assertFalse(cache.getByName(testProfile.getName()).isPresent())
        );
    }

    @Test
    void removeSkin() {
        UUID profileId = UUID.randomUUID();
        SkinProperty property = new SkinProperty(SkinPropertyTest.STEVE_VALUE, SkinPropertyTest.STEVE_SIGNATURE);
        cache.addSkin(profileId, property);

        cache.removeSkin(UUID.randomUUID());
        assertTrue(cache.getSkin(profileId).isPresent());

        cache.removeSkin(profileId);
        assertFalse(cache.getSkin(profileId).isPresent());
    }
}
