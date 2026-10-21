/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * — the two property names exist for a mechanical reason, so the reason itself is
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
        assertTrue(ForwardingAttributes.IDENTITY.matches(LEGACY_NAME_PATTERN));
        assertTrue(ForwardingAttributes.IDENTITY_SIGNATURE.matches(LEGACY_NAME_PATTERN));
        org.junit.jupiter.api.Assertions.assertEquals(16, ForwardingAttributes.IDENTITY_SIGNATURE.length());
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
