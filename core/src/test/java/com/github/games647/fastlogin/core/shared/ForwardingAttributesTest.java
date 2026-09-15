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
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.7.0/F17 — the two property names exist for a mechanical reason, so the reason itself is
 * pinned here instead of living only in a comment.
 *
 * <p>Paper rebuilds the player profile from BungeeCord's legacy handshake and discards every
 * property whose name does not match {@code \w{0,16}}. A rename that breaks that match would not
 * fail loudly anywhere: the attestation would simply stop arriving and the first-login dialog
 * would come back.</p>
 */
class ForwardingAttributesTest {

    /** The name filter Paper applies to legacy-forwarded profile properties. */
    private static final String LEGACY_NAME_PATTERN = "\\w{0,16}";

    @Test
    void legacyNameSurvivesPapersLegacyForwardingFilter() {
        assertTrue(ForwardingAttributes.PREMIUM_UUID_LEGACY.matches(LEGACY_NAME_PATTERN),
                "the BungeeCord property name must match Paper's legacy property filter,"
                        + " otherwise it is dropped without a trace");
    }

    @Test
    void velocityNameIsNotUsableOnTheLegacyTransport() {
        // this is the whole reason there are two names: the hyphens disqualify it
        assertFalse(ForwardingAttributes.PREMIUM_UUID.matches(LEGACY_NAME_PATTERN));
    }

    @Test
    void bothNamesAreAccepted() {
        assertTrue(ForwardingAttributes.isPremiumUuidProperty(ForwardingAttributes.PREMIUM_UUID));
        assertTrue(ForwardingAttributes.isPremiumUuidProperty(
                ForwardingAttributes.PREMIUM_UUID_LEGACY));
    }

    @Test
    void unrelatedNamesAreRejected() {
        assertFalse(ForwardingAttributes.isPremiumUuidProperty("textures"));
        assertFalse(ForwardingAttributes.isPremiumUuidProperty("flp-premium"));
        assertFalse(ForwardingAttributes.isPremiumUuidProperty(null));
    }
}
