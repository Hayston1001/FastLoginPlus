/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class EasyAuthStorageWarningTest {
    @Test
    void verifiesOnlyThePinnedSQLiteImplementation() {
        assertTrue(EasyAuth344Bridge.isVerifiedStorage("xyz.nikitacartes.easyauth.storage.database.SQLite"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "xyz.nikitacartes.easyauth.storage.database.MySQL",
        "xyz.nikitacartes.easyauth.storage.database.PostgreSQL",
        "xyz.nikitacartes.easyauth.storage.database.MongoDB"
    })
    void doesNotVerifyOtherOrMissingImplementations(String className) {
        assertFalse(EasyAuth344Bridge.isVerifiedStorage(className));
    }

    @Test
    void warnsOnceWithTheActualImplementationAndFailOpenRiskWithoutRejecting() throws Exception {
        EasyAuth344Bridge bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        Field easyAuth = EasyAuth344Bridge.class.getDeclaredField("easyAuth");
        easyAuth.setAccessible(true);
        easyAuth.set(bridge, EasyAuth.class);
        Logger logger = mock(Logger.class);

        assertDoesNotThrow(() -> bridge.warnUnverifiedStorage(logger));
        assertDoesNotThrow(() -> bridge.warnUnverifiedStorage(logger));

        ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(warning.capture(), eq(Database.class.getName()));
        verifyNoMoreInteractions(logger);
        assertTrue(warning.getValue().contains("unverified combination"));
        assertTrue(warning.getValue().contains("getUserData returns null on SQL exceptions"));
        assertTrue(warning.getValue().contains("read failures may be treated as no account (fail-open)"));
        assertTrue(warning.getValue().contains("supported combination is local SQLite on both sides"));
    }

    public static final class EasyAuth {
        public static final Database DB = new Database();
    }

    public static final class Database {
    }
}
