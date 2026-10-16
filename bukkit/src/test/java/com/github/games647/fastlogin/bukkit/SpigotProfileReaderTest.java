/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.protocol.IdentityClaim;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class SpigotProfileReaderTest {
    @Test
    void liveProfileIsPreferredOverTheLossyPublicWrapper() throws Exception {
        SpigotProfileReader.Snapshot read = SpigotProfileReader.read(new Carrier());
        assertEquals("getProfile", read.source);
        assertEquals(Arrays.asList("claim-value"), read.claims);
    }

    @Test
    void recordAccessorsAndFlattenedPropertyMapAreSupportedWithoutAuthlibLinkage() throws Exception {
        assertEquals(Arrays.asList("one", "two"), SpigotProfileReader.claimValues(new RecordProfile()));
    }

    @Test
    void missingProfileApiIsReportedRatherThanInventingAClaim() {
        assertThrows(NoSuchMethodException.class, () -> SpigotProfileReader.read(new Object()));
    }

    public static final class Carrier {
        public Object getProfile() { return new SpigotPolicyLoginTest.BeanProfile("claim-value"); }
        public Object getPlayerProfile() { throw new AssertionError("lossy wrapper must not be used first"); }
    }

    public static final class RecordProfile {
        public PropertyMap properties() { return new PropertyMap(); }
    }

    public static final class PropertyMap {
        public Collection<RecordProperty> values() {
            return Arrays.asList(new RecordProperty("textures", "not-a-claim"),
                    new RecordProperty(IdentityClaim.PROPERTY_NAME, "one"),
                    new RecordProperty(IdentityClaim.PROPERTY_NAME, "two"));
        }
    }

    public static final class RecordProperty {
        private final String name;
        private final String value;
        public RecordProperty(String name, String value) { this.name = name; this.value = value; }
        public String name() { return name; }
        public String value() { return value; }
    }
}
