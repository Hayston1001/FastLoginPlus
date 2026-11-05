/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FastLoginCoreConfigReadTest {

    @Test
    void quotedBooleanFallsBackToTheTemplateDefaultWithAWarning() {
        Configuration config = new Configuration();
        config.set("useSSL", "yes");
        Configuration defaults = new Configuration();
        defaults.set("useSSL", true);
        Logger logger = mock(Logger.class);

        assertTrue(FastLoginCore.readBoolean(config, defaults, "useSSL", logger));
        verify(logger).warn(contains("must be a boolean"), eq("useSSL"), eq("String"), eq(true));
    }

    @Test
    void numericStringForAnIntegerFallsBackToTheTemplateDefaultWithAWarning() {
        Configuration config = new Configuration();
        config.set("port", "3306");
        Configuration defaults = new Configuration();
        defaults.set("port", 3306);
        Logger logger = mock(Logger.class);

        assertEquals(3306, FastLoginCore.readInt(config, defaults, "port", logger));
        verify(logger).warn(contains("must be a number"), eq("port"), eq("String"), eq(3306));
    }

    @Test
    void wrongTypesFallBackToTheTemplateDefaultWithAWarning() {
        Configuration config = new Configuration();
        config.set("debug", 1);
        config.set("host", Arrays.asList("not", "a", "string"));
        Configuration defaults = new Configuration();
        defaults.set("debug", true);
        defaults.set("host", "127.0.0.1");
        Logger logger = mock(Logger.class);

        assertTrue(FastLoginCore.readBoolean(config, defaults, "debug", logger));
        assertEquals("127.0.0.1", FastLoginCore.readString(config, defaults, "host", logger));
        verify(logger).warn(contains("must be a boolean"), eq("debug"), eq("Integer"), eq(true));
        verify(logger).warn(contains("must be a string"), eq("host"), any(), eq("127.0.0.1"));
    }

    @Test
    void validAndMissingValuesKeepTheConfiguredBehaviour() {
        Configuration config = new Configuration();
        config.set("useSSL", false);
        config.set("port", 3307);
        Configuration defaults = new Configuration();
        defaults.set("useSSL", true);
        defaults.set("port", 3306);
        defaults.set("host", "127.0.0.1");
        Logger logger = mock(Logger.class);

        assertFalse(FastLoginCore.readBoolean(config, defaults, "useSSL", logger));
        assertEquals(3307, FastLoginCore.readInt(config, defaults, "port", logger));
        assertEquals("127.0.0.1", FastLoginCore.readString(config, defaults, "host", logger));
        verifyNoInteractions(logger);
    }

    @Test
    void absentTemplateDefaultsUseTheNeutralValue() {
        Configuration config = new Configuration();
        config.set("useSSL", "yes");
        Logger logger = mock(Logger.class);

        assertFalse(FastLoginCore.readBoolean(config, null, "useSSL", logger));
        assertEquals(0, FastLoginCore.readInt(config, null, "port", logger));
        assertEquals("", FastLoginCore.readString(config, null, "host", logger));
        verify(logger).warn(contains("must be a boolean"), eq("useSSL"), eq("String"), eq(false));
    }
}
