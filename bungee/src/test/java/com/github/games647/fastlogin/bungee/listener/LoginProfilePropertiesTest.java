/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
    void offlineProfilesCanCarryAnExplicitUnverifiedEarlyClaimInBothShapes() {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        LegacyConnection legacy = new LegacyConnection();
        ModernConnection modern = new ModernConnection();
        assertTrue(LoginProfileProperties.ensureOfflineProfile(legacy, "TestUser", uuid, failures::add));
        assertTrue(LoginProfileProperties.ensureOfflineProfile(modern, "TestUser", uuid, failures::add));
        com.github.games647.fastlogin.protocol.IdentityClaim claim =
                new com.github.games647.fastlogin.protocol.IdentityClaim(
                        com.github.games647.fastlogin.protocol.IdentityClaim.Decision.OFFLINE,
                        "TestUser", uuid, null);
        assertTrue(LoginProfileProperties.attach(legacy.loginProfile, "flp_identity", claim.encode(), failures::add));
        assertTrue(LoginProfileProperties.attach(modern.loginProfile, "flp_identity", claim.encode(), failures::add));
        assertEquals(claim.encode(), legacy.loginProfile.properties[0].getValue());
        assertEquals(claim.encode(), modern.loginProfile.properties[0].getValue());
        assertTrue(failures.isEmpty());
    }

    static final class LegacyConnection { private LegacyProfile loginProfile; }
    static final class ModernConnection { private ModernProfile loginProfile; }

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
        public LegacyProfile(String id, String name, LegacyProperty[] properties) { this.properties = properties; }
    }

    /** Stand-in for a login profile that carries the new property type. */
    static final class ModernProfile {

        private ModernProperty[] properties;

        ModernProfile(ModernProperty[] properties) {
            this.properties = properties;
        }
        public ModernProfile(String id, String name, ModernProperty[] properties) { this.properties = properties; }
    }
}
