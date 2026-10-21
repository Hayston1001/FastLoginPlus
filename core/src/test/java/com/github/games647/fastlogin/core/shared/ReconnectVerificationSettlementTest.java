/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconnectVerificationSettlementTest {
    private static final String NAME = "TestUser";
    private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core;
    private SQLStorage storage;
    private MojangResolver resolver;
    private StoredProfile profile;
    private TestJoinManagement management;
    private final AtomicLong now = new AtomicLong();

    @BeforeEach
    void setUp() throws Exception {
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        core = spy(new FastLoginCore<>(plugin, now::get));
        storage = mock(SQLStorage.class);
        resolver = mock(MojangResolver.class);
        Configuration config = new Configuration();
        config.set("secondAttemptCracked", true);
        config.set("nameChangeCheck", false);
        config.set("autoRegister", true);
        config.set("offline-whitelist", false);
        config.set("mojang-retry-count", 1);
        config.set("mojang-retry-delay", 0);
        doReturn(config).when(core).getConfig();
        doReturn(storage).when(core).getStorage();
        doReturn(resolver).when(core).getResolver();
        when(resolver.hasJoined(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(resolver.findProfile(NAME)).thenReturn(Optional.of(new Profile(PREMIUM_UUID, NAME)));
        Field probe = FastLoginCore.class.getDeclaredField("sessionProbe");
        probe.setAccessible(true);
        probe.set(core, new SessionServerProbe(resolver, FastLoginCore.SESSION_PROBE_CACHE_MS));
        doAnswer(call -> ((Supplier<?>) call.getArgument(1)).get())
                .when(storage).withNameLock(anyString(), any(Supplier.class));
        profile = new StoredProfile(1, PREMIUM_UUID, NAME, false, FloodgateState.FALSE,
                "127.0.0.1", Instant.now());
        when(storage.loadProfile(NAME)).thenAnswer(call -> profile);
        management = new TestJoinManagement(core, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void expiredReconnectSettlesBeforeLoadingExistingOrNewProfile(boolean existing) {
        if (!existing) {
            profile = new StoredProfile(null, NAME, false, FloodgateState.FALSE, "127.0.0.1");
        }
        management.onLogin(NAME, new TestSource());
        assertEquals(1, management.premiumRequests);
        assertEquals(0, core.getDefiniteFailures(NAME));
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        when(storage.loadProfile(NAME)).thenAnswer(call -> {
            assertEquals(1, core.getDefiniteFailures(NAME), "settlement precedes the profile load");
            return profile;
        });

        // No sweep occurs between the two connections.
        management.onLogin(NAME, new TestSource());

        assertEquals(1, management.premiumRequests);
        assertEquals(1, management.crackedSessions);
        assertEquals(1, core.getDefiniteFailures(NAME));
        assertTrue(profile.isSameNameCracked());
        core.settlePendingVerifications();
        assertEquals(1, core.getDefiniteFailures(NAME), "the later sweep cannot charge the same attempt twice");
    }

    @Test
    void freshReconnectGetsItsOwnGraceWithoutHidingAnEventualFailure() throws Exception {
        management.onLogin(NAME, new TestSource());
        now.set(FastLoginCore.VERIFICATION_GRACE_MS - 1);

        management.onLogin(NAME, new TestSource());

        assertEquals(0, core.getDefiniteFailures(NAME));
        assertEquals(2, management.premiumRequests);
        assertEquals(0, management.crackedSessions);
        assertFalse(profile.isSameNameCracked(), "a retry during grace must not create a cracked observation");
        verify(resolver, never()).hasJoined(anyString(), anyString(), any());
        now.incrementAndGet();
        core.settlePendingVerifications();
        assertEquals(0, core.getDefiniteFailures(NAME), "the retry has a fresh grace period");
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS - 1);
        core.settlePendingVerifications();
        assertEquals(1, core.getDefiniteFailures(NAME), "an abandoned retry still expires without another login");
        management.onLogin(NAME, new TestSource());
        assertEquals(1, management.crackedSessions);
        assertTrue(profile.isSameNameCracked());
    }

    @Test
    void unreachableSessionServerDoesNotChargeThePreviousAttempt() throws Exception {
        management.onLogin(NAME, new TestSource());
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        when(resolver.hasJoined(anyString(), anyString(), any())).thenThrow(new IOException("unreachable"));

        management.onLogin(NAME, new TestSource());

        assertEquals(0, core.getDefiniteFailures(NAME));
        assertEquals(2, management.premiumRequests, "an outage must not trigger cracked fallback");
        assertEquals(0, management.crackedSessions);
        // Discard the new attempt, then prove the dropped old attempt cannot reappear.
        core.dropPendingVerification(NAME);
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        core.settlePendingVerifications(true);
        assertEquals(0, core.getDefiniteFailures(NAME));
    }

    @Test
    void lateSuccessClearsTheFailureSettledByReconnect() {
        core.markPendingVerification(NAME);
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        management.onLogin(NAME, new TestSource());
        assertEquals(1, core.getDefiniteFailures(NAME));

        core.resetVerificationState(NAME.toUpperCase(Locale.ROOT));
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        core.settlePendingVerifications();

        assertEquals(0, core.getDefiniteFailures(NAME));
    }

    @Test
    void reconnectSettlesOnlyItsOwnCaseInsensitiveNameAndReusesTheProbeCache() throws Exception {
        core.markPendingVerification(NAME.toLowerCase(Locale.ROOT));
        core.markPendingVerification("OtherUser");
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);

        management.onLogin(NAME, new TestSource());

        assertEquals(1, core.getDefiniteFailures(NAME));
        assertEquals(0, core.getDefiniteFailures("OtherUser"));
        core.settlePendingVerification("OTHERUSER");
        assertEquals(1, core.getDefiniteFailures("OtherUser"), "the other pending mark was retained");
        verify(resolver, times(1)).hasJoined(anyString(), anyString(), any());
        core.settlePendingVerifications();
        assertEquals(1, core.getDefiniteFailures(NAME));
        assertEquals(1, core.getDefiniteFailures("OtherUser"));
    }

    @Test
    void sweepBeforeReconnectDoesNotChargeTheSameAttemptTwice() {
        core.markPendingVerification(NAME);
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        core.settlePendingVerifications();

        management.onLogin(NAME, new TestSource());

        assertEquals(1, core.getDefiniteFailures(NAME));
        assertEquals(1, management.crackedSessions);
        assertEquals(0, management.premiumRequests);
    }

    @Test
    void reconnectAndSweepShareOneAtomicSettlement() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 200; attempt++) {
                core.markPendingVerification(NAME);
                now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> reconnect = pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    core.settlePendingVerification(NAME);
                    return null;
                });
                Future<?> sweep = pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    core.settlePendingVerifications();
                    return null;
                });
                reconnect.get(5, TimeUnit.SECONDS);
                sweep.get(5, TimeUnit.SECONDS);
                assertEquals(1, core.getDefiniteFailures(NAME));
                core.resetVerificationState(NAME);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void firstLoginDoesNotProbeOrSettleItsOwnNewAttempt() throws Exception {
        management.onLogin(NAME, new TestSource());

        assertEquals(1, management.premiumRequests);
        assertEquals(0, core.getDefiniteFailures(NAME));
        verify(resolver, never()).hasJoined(anyString(), anyString(), any());
        core.settlePendingVerifications();
        assertEquals(0, core.getDefiniteFailures(NAME), "a fresh attempt must not be consumed by a sweep");
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        core.settlePendingVerifications();
        assertEquals(1, core.getDefiniteFailures(NAME), "a settlement after grace may consume the new mark");
    }

    @Test
    void settlementRunsBeforeTheBedrockEarlyReturn() {
        BedrockService<?> bedrock = mock(BedrockService.class);
        core.markPendingVerification(NAME);
        now.addAndGet(FastLoginCore.VERIFICATION_GRACE_MS);
        when(bedrock.isBedrockConnection(NAME)).thenAnswer(call -> {
            assertEquals(1, core.getDefiniteFailures(NAME));
            return true;
        });
        when(bedrock.performChecks(anyString(), any())).thenReturn(true);
        new TestJoinManagement(core, bedrock).onLogin(NAME, new TestSource());

        verify(storage, never()).loadProfile(anyString());
        assertEquals(1, core.getDefiniteFailures(NAME));
    }

    private static final class TestSource implements LoginSource {
        @Override
        public void enableOnlinemode() {
        }

        @Override
        public void kick(String message) {
        }

        @Override
        public InetSocketAddress getAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }
    }

    private static final class TestJoinManagement extends JoinManagement<Object, Object, TestSource> {
        private int premiumRequests;
        private int crackedSessions;

        TestJoinManagement(FastLoginCore<Object, Object, PlatformPlugin<Object>> core, BedrockService<?> bedrock) {
            super(core, null, bedrock);
        }

        @Override
        public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, TestSource source,
                                                                 StoredProfile profile) {
            return mock(FastLoginPreLoginEvent.class);
        }

        @Override
        public void requestPremiumLogin(TestSource source, StoredProfile profile, String username,
                                        boolean registered) {
            premiumRequests++;
            core.markPendingVerification(username);
        }

        @Override
        public void startCrackedSession(TestSource source, StoredProfile profile, String username) {
            crackedSessions++;
        }
    }
}
