/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the record-driven routing and the same-name cracked fallback:
 * what the stored observations and the consent do to the login path of a name.
 */
class JoinManagementTest {

    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core;
    private Configuration config;
    private SQLStorage storage;

    @BeforeEach
    void setUp() {
        core = mock(FastLoginCore.class);
        config = mock(Configuration.class);
        storage = mock(SQLStorage.class);
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));

        when(core.getConfig()).thenReturn(config);
        when(core.getStorage()).thenReturn(storage);
        doReturn(plugin).when(core).getPlugin();
        // the production windows run inside withNameLock - the mock must
        // execute the passed lambda so the wrapped save still happens
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(storage).withNameLock(anyString(), any(Supplier.class));
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(storage).withNameLock(anyString(), any(Runnable.class));
    }

    // ---- routing: consent wins over every observation ----

    @Test
    void consentedNameAlwaysRetriesVerification() {
        // even with the same-name cracked observation recorded and an
        // unverified-attempt streak, a consented name must be verified on every
        // connection - otherwise a griefer could drag a premium name into the
        // password flow by connecting with a cracked client
        StoredProfile profile = existingRow(UUID.randomUUID(), true, true);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(1);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(1, mgmt.premiumRequests);
        assertEquals(0, mgmt.crackedSessions);
    }

    // ---- routing: the premium UUID observation keeps the name verified ----

    @Test
    void premiumUuidObservationRoutesThroughVerification() {
        StoredProfile profile = existingRow(UUID.randomUUID(), false, false);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(0);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(1, mgmt.premiumRequests);
        assertEquals(0, mgmt.crackedSessions);
        verify(storage, never()).save(any());
    }

    // ---- fallback: one unverified attempt is enough ----

    @Test
    void oneUnverifiedAttemptOpensTheCrackedFallback() {
        StoredProfile profile = existingRow(UUID.randomUUID(), false, false);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(1);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(0, mgmt.premiumRequests);
        assertEquals(1, mgmt.crackedSessions);
        assertTrue(profile.isSameNameCracked(), "the observation must be recorded per name");
        verify(storage).save(profile);
        verify(core).queueSameNameCrackedNotice("TestUser");
    }

    @Test
    void onlyTheFirstConfirmationQueuesTheNotice() {
        // the record already exists on later joins: the one-time chat notice is
        // owed by the confirmation that flipped the flag, not by every one
        StoredProfile profile = existingRow(null, false, false);
        TestJoinManagement mgmt = createManagement();
        mgmt.confirmForTest("TestUser", profile);
        mgmt.confirmForTest("TestUser", profile);

        assertTrue(profile.isSameNameCracked());
        verify(core, times(1)).queueSameNameCrackedNotice("TestUser");
    }

    @Test
    void observedNameSkipsTheDoomedVerification() {
        // the persistent observation sends the name straight to the
        // password flow, no failed connection first
        StoredProfile profile = existingRow(null, false, true);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(0);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(0, mgmt.premiumRequests);
        assertEquals(1, mgmt.crackedSessions);
        verify(storage, never()).save(any());
        verify(core, never()).queueSameNameCrackedNotice(any());
    }

    @Test
    void plainCrackedRowKeepsThePasswordFlow() {
        StoredProfile profile = existingRow(null, false, false);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(1);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(0, mgmt.premiumRequests);
        assertEquals(1, mgmt.crackedSessions);
        // a row without any premium observation is never marked as same-name cracked
        assertFalse(profile.isSameNameCracked());
        verify(storage, never()).save(any());
    }

    // ---- fallback for names without a row ----

    @Test
    void newPlayerFallbackRecordsObservation() {
        StoredProfile profile = newRow();
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(1);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(1, mgmt.crackedSessions);
        assertTrue(profile.isSameNameCracked());
        verify(storage).save(profile);
    }

    @Test
    void fallbackDisabledTreatsAnObservationAsNeverRecorded() throws Exception {
        // with secondAttemptCracked off a marked name is "as if never
        // recorded" - a row that holds nothing but the observation gets the
        // unknown-name decision again, including the premium check
        StoredProfile profile = existingRow(null, false, true);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(0);
        when(config.get("secondAttemptCracked")).thenReturn(false);
        when(config.get("nameChangeCheck")).thenReturn(false);
        when(config.get("autoRegister")).thenReturn(true);
        when(config.get("offline-whitelist")).thenReturn(false);
        when(config.get("mojang-retry-count")).thenReturn(1);
        when(config.get("mojang-retry-delay")).thenReturn(100L);
        MojangResolver resolver = mock(MojangResolver.class);
        when(core.getResolver()).thenReturn(resolver);
        when(resolver.findProfile("TestUser"))
                .thenReturn(Optional.of(new Profile(UUID.randomUUID(), "TestUser")));

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(1, mgmt.premiumRequests, "the marked name must verify again");
        assertEquals(0, mgmt.crackedSessions);
    }

    @Test
    void fallbackDisabledRetriesVerificationForObservedUuidRows() {
        // the premium UUID observation of a marked row is still intact, so
        // the name verifies again; a passing verification then clears the mark
        StoredProfile profile = existingRow(UUID.randomUUID(), false, true);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(1);
        when(config.get("secondAttemptCracked")).thenReturn(false);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(1, mgmt.premiumRequests);
        assertEquals(0, mgmt.crackedSessions);
    }

    @Test
    void fallbackDisabledKeepsVerificationMandatory() {
        // secondAttemptCracked=false: a name must verify, no observation is made
        StoredProfile profile = newRow();
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(1);
        when(config.get("secondAttemptCracked")).thenReturn(false);
        when(config.get("nameChangeCheck")).thenReturn(false);
        when(config.get("autoRegister")).thenReturn(false);
        when(config.get("offline-whitelist")).thenReturn(false);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(0, mgmt.premiumRequests);
        assertEquals(1, mgmt.crackedSessions);
        assertFalse(profile.isSameNameCracked());
        verify(storage, never()).save(any());
    }

    // ---- an unfinished name lookup is "could not tell", never "not premium" ----

    @Test
    void lookupFailureKicksInsteadOfGuessing() throws Exception {
        // rate limit / network trouble / interruption: nothing may be degraded,
        // created or recorded — a new premium player must not end up in a
        // password account that then never verifies again
        StoredProfile profile = newRow();
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(0);
        when(config.get("secondAttemptCracked")).thenReturn(true);
        when(config.get("nameChangeCheck")).thenReturn(true);
        when(config.get("autoRegister")).thenReturn(true);
        when(config.get("offline-whitelist")).thenReturn(false);
        when(config.get("mojang-retry-count")).thenReturn(1);
        when(config.get("mojang-retry-delay")).thenReturn(100L);
        when(core.getMessage("auth-server-unreachable")).thenReturn("auth-server-unreachable");
        MojangResolver resolver = mock(MojangResolver.class);
        when(core.getResolver()).thenReturn(resolver);
        when(resolver.findProfile("TestUser")).thenThrow(new java.io.IOException("connect timed out"));

        TestSource source = new TestSource();
        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", source);

        assertEquals("auth-server-unreachable", source.kicked, "the player is told why, not degraded");
        assertEquals(0, mgmt.crackedSessions);
        assertEquals(0, mgmt.premiumRequests);
        assertFalse(profile.isSameNameCracked(), "an unfinished lookup counts nothing");
        verify(storage, never()).save(any());
    }

    @Test
    void rateLimitedLookupAlsoKicksInsteadOfGuessing() throws Exception {
        StoredProfile profile = newRow();
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(0);
        when(config.get("secondAttemptCracked")).thenReturn(true);
        when(config.get("nameChangeCheck")).thenReturn(true);
        when(config.get("autoRegister")).thenReturn(false);
        when(config.get("offline-whitelist")).thenReturn(false);
        when(config.get("mojang-retry-count")).thenReturn(1);
        when(config.get("mojang-retry-delay")).thenReturn(100L);
        when(core.getMessage("auth-server-unreachable")).thenReturn("auth-server-unreachable");
        MojangResolver resolver = mock(MojangResolver.class);
        when(core.getResolver()).thenReturn(resolver);
        when(resolver.findProfile("TestUser")).thenThrow(new RateLimitException("TestUser"));

        TestSource source = new TestSource();
        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", source);

        assertEquals("auth-server-unreachable", source.kicked);
        assertEquals(0, mgmt.crackedSessions);
    }

    @Test
    void definiteNoSuchNameGoesToThePasswordFlow() throws Exception {
        // the only "definitely not premium" verdict: the lookup succeeded and
        // Mojang has no such name — that name may register like any cracked one
        StoredProfile profile = newRow();
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(0);
        when(config.get("secondAttemptCracked")).thenReturn(true);
        when(config.get("nameChangeCheck")).thenReturn(true);
        when(config.get("autoRegister")).thenReturn(false);
        when(config.get("offline-whitelist")).thenReturn(false);
        when(config.get("mojang-retry-count")).thenReturn(1);
        when(config.get("mojang-retry-delay")).thenReturn(100L);
        MojangResolver resolver = mock(MojangResolver.class);
        when(core.getResolver()).thenReturn(resolver);
        when(resolver.findProfile("TestUser")).thenReturn(Optional.empty());

        TestSource source = new TestSource();
        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", source);

        assertNull(source.kicked);
        assertEquals(1, mgmt.crackedSessions);
        assertEquals(0, mgmt.premiumRequests);
    }

    // ---- pre-login event coverage ----

    @Test
    void preLoginEventFiresOnceWithTheLoadedProfileBeforeTheExistingDecision() {
        StoredProfile profile = existingRow(UUID.randomUUID(), true, false);
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(0);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(1, mgmt.eventCalls);
        assertSame(profile, mgmt.eventProfile);
        assertEquals(1, mgmt.premiumRequests);
    }

    @Test
    void preLoginEventFiresOnceForANewPlayerBeforeTheCrackedFallback() {
        StoredProfile profile = newRow();
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(core.getDefiniteFailures("TestUser")).thenReturn(1);
        when(config.get("secondAttemptCracked")).thenReturn(true);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(1, mgmt.eventCalls);
        assertSame(profile, mgmt.eventProfile);
        assertEquals(1, mgmt.crackedSessions);
    }

    @Test
    void preLoginEventDoesNotFireWhenTheProfileCouldNotBeRead() {
        when(storage.loadProfile("TestUser")).thenReturn(null);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(0, mgmt.eventCalls);
        assertEquals(0, mgmt.premiumRequests);
        assertEquals(0, mgmt.crackedSessions);
    }

    @Test
    void preLoginEventSkipsAMigratedBedrockRecord() {
        StoredProfile profile = new StoredProfile(1, UUID.randomUUID(), "TestUser", false,
                FloodgateState.TRUE, "127.0.0.1", Instant.now());
        when(storage.loadProfile("TestUser")).thenReturn(profile);

        TestJoinManagement mgmt = createManagement();
        mgmt.onLogin("TestUser", new TestSource());

        assertEquals(0, mgmt.eventCalls);
        assertEquals(0, mgmt.premiumRequests);
        assertEquals(0, mgmt.crackedSessions);
    }

    private StoredProfile existingRow(UUID uuid, boolean consented, boolean observed) {
        StoredProfile profile = new StoredProfile(1, uuid, "TestUser", consented, FloodgateState.FALSE,
                "127.0.0.1", Instant.now());
        profile.setSameNameCracked(observed);
        return profile;
    }

    private StoredProfile newRow() {
        return new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
    }

    private TestJoinManagement createManagement() {
        return new TestJoinManagement(core);
    }

    private static class TestSource implements LoginSource {

        private String kicked;

        @Override
        public void enableOnlinemode() {
        }

        @Override
        public void kick(String message) {
            kicked = message;
        }

        @Override
        public InetSocketAddress getAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }
    }

    private static class TestJoinManagement extends JoinManagement<Object, Object, TestSource> {

        private int premiumRequests;
        private int crackedSessions;
        private int eventCalls;
        private StoredProfile eventProfile;

        TestJoinManagement(FastLoginCore<Object, Object, PlatformPlugin<Object>> core) {
            super(core, null, null);
        }

        @Override
        public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, TestSource source,
                                                                 StoredProfile profile) {
            eventCalls++;
            eventProfile = profile;
            return mock(FastLoginPreLoginEvent.class);
        }

        @Override
        public void requestPremiumLogin(TestSource source, StoredProfile profile, String username,
                                        boolean registered) {
            premiumRequests++;
        }

        @Override
        public void startCrackedSession(TestSource source, StoredProfile profile, String username) {
            crackedSessions++;
        }

        void confirmForTest(String username, StoredProfile profile) {
            confirmSameNameCracked(username, profile);
        }
    }
}
