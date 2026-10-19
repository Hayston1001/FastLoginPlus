/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim.Consent;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that the declared facts are a snapshot of the record (D-03 E-3):
 * observations and consent come from the stored row, never from transient
 * per-connection state.
 */
class LoginSessionTest {

    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core;
    private Configuration config;

    @BeforeEach
    void setUp() {
        core = mock(FastLoginCore.class);
        config = mock(Configuration.class);
        when(core.getConfig()).thenReturn(config);
    }

    @Test
    void factsReadConsentFromTheRecord() {
        StoredProfile consented = new StoredProfile(1, UUID.randomUUID(), "TestUser", true,
                FloodgateState.FALSE, "127.0.0.1", Instant.now());
        LoginSession session = new TestSession("TestUser", true, consented);
        session.computeFacts(core);
        assertEquals(Consent.YES, session.getFacts().consent());

        StoredProfile notConsented = new StoredProfile(1, UUID.randomUUID(), "TestUser", false,
                FloodgateState.FALSE, "127.0.0.1", Instant.now());
        LoginSession other = new TestSession("TestUser", true, notConsented);
        other.computeFacts(core);
        assertEquals(Consent.NO, other.getFacts().consent());
    }

    @Test
    void factsReportTheSameNameCrackedObservation() {
        when(config.getBoolean("secondAttemptCracked")).thenReturn(true);
        StoredProfile observed = new StoredProfile(1, null, "TestUser", false,
                FloodgateState.FALSE, "127.0.0.1", Instant.now());
        observed.setSameNameCracked(true);
        LoginSession session = new TestSession("TestUser", false, observed);
        session.computeFacts(core);

        assertTrue(session.getFacts().sameNameCracked(),
                "an unverified connection under an observed premium name is the observation");
        assertFalse(session.getFacts().newPlayer(), "the observation is a record of its own");
    }

    @Test
    void factsTreatADisabledObservationAsAbsent() {
        // F-12: with secondAttemptCracked off the stored mark is dormant and
        // must not even show up in the declared facts
        when(config.getBoolean("secondAttemptCracked")).thenReturn(false);
        StoredProfile observed = new StoredProfile(1, null, "TestUser", false,
                FloodgateState.FALSE, "127.0.0.1", Instant.now());
        observed.setSameNameCracked(true);
        LoginSession session = new TestSession("TestUser", false, observed);
        session.computeFacts(core);

        assertFalse(session.getFacts().sameNameCracked(),
                "a dormant observation reads as never recorded");
    }

    @Test
    void verifiedConnectionIsNotSameNameCracked() {
        StoredProfile observed = new StoredProfile(1, null, "TestUser", false,
                FloodgateState.FALSE, "127.0.0.1", Instant.now());
        observed.setSameNameCracked(true);
        LoginSession session = new TestSession("TestUser", false, observed);
        session.setUuid(UUID.randomUUID());
        session.computeFacts(core);

        assertFalse(session.getFacts().sameNameCracked(), "this connection passed the verification");
    }

    @Test
    void factsMarkANewPlayerFromTheMissingRecord() {
        StoredProfile fresh = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        LoginSession session = new TestSession("TestUser", false, fresh);
        session.computeFacts(core);

        assertTrue(session.getFacts().newPlayer());
        assertFalse(session.getFacts().sameNameCracked());
        assertEquals(Consent.NO, session.getFacts().consent());
    }

    private static class TestSession extends LoginSession {

        TestSession(String username, boolean registered, StoredProfile profile) {
            super(username, registered, profile);
        }
    }
}
