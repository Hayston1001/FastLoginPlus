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
package com.github.games647.fastlogin.bukkit.listener;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.model.skin.Textures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Paper pre-login hook runs at {@code HIGHEST}, i.e. after SkinsRestorer's own handler (which
 * uses the default priority and fills the profile with the player's custom skin). Whatever this
 * hook writes therefore wins, so it may only ever write SkinsRestorer's own value.
 */
class PaperCacheListenerSkinTest {

    /**
     * Real-machine regression (2026-09-17): the hook used to write an empty {@code textures}
     * property as a placeholder. SkinsRestorer decides "has online properties" by the property SET
     * being non-empty, not by the value, so an empty value both marked the profile as
     * SkinsRestorer-populated and destroyed the skin that plugin had just applied — players
     * connected with no skin at all. The stored skin must be forwarded verbatim instead.
     */
    @Test
    void forwardsSkinsRestorerSkinInsteadOfAnEmptyPlaceholder() {
        ProfileProperty property = PaperCacheListener.skinPropertyFor(new SkinProperty("value", "signature"));

        assertNotNull(property);
        assertEquals(Textures.KEY, property.getName());
        assertEquals("value", property.getValue());
        assertEquals("signature", property.getSignature());
    }

    /**
     * No stored skin (or an unavailable SkinsRestorer API) must leave the profile untouched:
     * writing nothing at all is what lets the plugin's own handler keep its result.
     */
    @Test
    void writesNothingWithoutASkin() {
        assertNull(PaperCacheListener.skinPropertyFor(null));
    }

    /**
     * Belt and braces for the same incident: even if SkinsRestorer answers with an empty value,
     * that value must never reach the profile, because it would recreate the wiped-skin symptom.
     */
    @Test
    void neverWritesAnEmptyValue() {
        assertNull(PaperCacheListener.skinPropertyFor(new SkinProperty("", "signature")));
        assertNull(PaperCacheListener.skinPropertyFor(new SkinProperty(null, null)));
    }
}
