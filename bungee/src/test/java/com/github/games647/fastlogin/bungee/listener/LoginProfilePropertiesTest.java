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
package com.github.games647.fastlogin.bungee.listener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * — the helper exists because BungeeCord moved the property class upstream while this
 * module compiles against the pre-move artifact. Both shapes therefore have to work, and the
 * stand-ins below reproduce them without touching BungeeCord: each profile carries a
 * {@code properties} field whose component type differs, which is exactly the difference the
 * helper has to discover.
 */
class LoginProfilePropertiesTest {

    private static final String ATTESTATION = "flp_premium_uuid";
    private static final String UUID_VALUE = "272cb3e9-24d3-4dcd-b47c-4b786e7421f8";

    private final List<Throwable> failures = new ArrayList<>();

    @Test
    void attachesToTheOldShape() {
        LegacyProfile profile = new LegacyProfile(new LegacyProperty[]{
            new LegacyProperty("textures", "skin-data")
        });

        assertTrue(LoginProfileProperties.attach(profile, ATTESTATION, UUID_VALUE, failures::add),
                () -> "attach failed: " + failures);

        assertEquals(2, profile.properties.length);
        assertEquals("textures", profile.properties[0].getName());
        assertEquals(ATTESTATION, profile.properties[1].getName());
        assertEquals(UUID_VALUE, profile.properties[1].getValue());
        assertTrue(failures.isEmpty());
    }

    @Test
    void attachesToTheNewShape() {
        ModernProfile profile = new ModernProfile(new ModernProperty[]{
            new ModernProperty("textures", "skin-data")
        });

        assertTrue(LoginProfileProperties.attach(profile, ATTESTATION, UUID_VALUE, failures::add),
                () -> "attach failed: " + failures);

        assertEquals(2, profile.properties.length);
        assertEquals("textures", profile.properties[0].getName());
        assertEquals(ATTESTATION, profile.properties[1].getName());
        assertEquals(UUID_VALUE, profile.properties[1].getValue());
        assertTrue(failures.isEmpty());
    }

    @Test
    void attachingTwiceLeavesOneCopyOfTheProperty() {
        LegacyProfile profile = new LegacyProfile(new LegacyProperty[0]);

        assertTrue(LoginProfileProperties.attach(profile, ATTESTATION, UUID_VALUE, failures::add),
                () -> "attach failed: " + failures);
        assertTrue(LoginProfileProperties.attach(profile, ATTESTATION, UUID_VALUE, failures::add),
                () -> "attach failed: " + failures);

        assertEquals(1, profile.properties.length);
        assertEquals(ATTESTATION, profile.properties[0].getName());
    }

    @Test
    void attachingWorksWithoutExistingProperties() {
        ModernProfile profile = new ModernProfile(null);

        assertTrue(LoginProfileProperties.attach(profile, ATTESTATION, UUID_VALUE, failures::add),
                () -> "attach failed: " + failures);

        assertEquals(1, profile.properties.length);
        assertEquals(ATTESTATION, profile.properties[0].getName());
    }

    @Test
    void clearingEmptyThePropertyArray() {
        ModernProfile profile = new ModernProfile(new ModernProperty[]{
            new ModernProperty("textures", "skin-data")
        });

        assertTrue(LoginProfileProperties.clear(profile, failures::add));

        // an empty array of the right component type, not null: BungeeCord serialises either,
        // but the typed empty array is what the previous implementation produced
        assertEquals(0, profile.properties.length);
        assertTrue(failures.isEmpty());
    }

    @Test
    void unknownShapeIsReportedInsteadOfThrown() {
        Object profileWithoutProperties = new Object();

        assertFalse(LoginProfileProperties.attach(profileWithoutProperties, ATTESTATION, UUID_VALUE,
                failures::add));
        assertFalse(LoginProfileProperties.clear(profileWithoutProperties, failures::add));

        assertEquals(2, failures.size());
    }

    @Test
    void nullProfileIsNotAFailure() {
        assertFalse(LoginProfileProperties.attach(null, ATTESTATION, UUID_VALUE, failures::add));
        assertFalse(LoginProfileProperties.clear(null, failures::add));

        assertTrue(failures.isEmpty(), "a null profile is normal on offline-mode connections");
    }

    /** Stand-in for the pre-#3855 {@code net.md_5.bungee.protocol.Property}. */
    static final class LegacyProperty {

        private final String name;
        private final String value;

        public LegacyProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    /** Stand-in for the post-#3855 {@code net.md_5.bungee.protocol.data.Property}. */
    static final class ModernProperty {

        private final String name;
        private final String value;

        public ModernProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    /** Stand-in for a login profile that carries the old property type. */
    static final class LegacyProfile {

        private LegacyProperty[] properties;

        LegacyProfile(LegacyProperty[] properties) {
            this.properties = properties;
        }
    }

    /** Stand-in for a login profile that carries the new property type. */
    static final class ModernProfile {

        private ModernProperty[] properties;

        ModernProfile(ModernProperty[] properties) {
            this.properties = properties;
        }
    }
}
