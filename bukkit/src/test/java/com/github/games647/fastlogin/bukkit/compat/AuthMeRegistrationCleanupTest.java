/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.compat;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.hook.AuthMeHook;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin.Registration;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockSession;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import fr.xephi.authme.api.v3.AuthMeApi;
import net.md_5.bungee.config.Configuration;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthMeRegistrationCleanupTest {
    private static final String NAME = "bedrock";
    private static final String HASH = "DO-NOT-LOG-HASH";
    private static final String SALT = "DO-NOT-LOG-SALT";
    private final Logger logger = mock(Logger.class);
    private final AuthMeVersionDetector detector = mock(AuthMeVersionDetector.class);
    private final Source source = new Source();
    private final Players players = new Players();
    private final Pending pending = new Pending();
    private final Verifier verifier = new Verifier();
    private final ConcurrentMap<String, Object> playerEntries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> pendingEntries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> verifiedEntries = new ConcurrentHashMap<>();
    private AuthMePremiumIntegrator integrator;

    public interface PreJoinApi {
        void forceLoginFromProxy(String name);
    }

    @BeforeEach
    void setUp() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        when(plugin.getLog()).thenReturn(logger);
        when(detector.isAuthMe6()).thenReturn(true);
        integrator = new AuthMePremiumIntegrator(plugin, detector);
        players.cache = playerEntries;
        pending.pending = pendingEntries;
        verifier.verified = verifiedEntries;
        set("dataSource", source);
        set("playerCache", players);
        set("pendingPremiumCache", pending);
        set("premiumLoginVerifier", verifier);
        playerEntries.put(NAME, source.row);
        pendingEntries.put(NAME, new Object());
        verifiedEntries.put(NAME, new Object());
    }

    @Test
    void matchingSnapshotDeletesSynchronouslyClearsCachesAndVerifiesReadback() {
        source.expectedThread = Thread.currentThread();
        Registration registration = capture();
        try (MockedStatic<AuthMeApi> api = mockStatic(AuthMeApi.class);
                MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertTrue(integrator.deleteRegistration(registration));
            assertNull(source.row);
            assertEquals(Arrays.asList("read", "remove", "read"), source.operations);
            assertTrue(playerEntries.isEmpty());
            assertTrue(pendingEntries.isEmpty());
            assertTrue(verifiedEntries.isEmpty());
            api.verifyNoInteractions();
            bukkit.verifyNoInteractions();
        }
        assertSafeLogs(registration);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void actualCoreHookAndIntegratorDeleteOnlyTheUnchangedCapturedRow(boolean replaced) {
        FastLoginBukkit bridge = mock(FastLoginBukkit.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(bridge.getCore()).thenReturn(core);
        when(bridge.getLog()).thenReturn(logger);
        when(bridge.getAuthMePremiumIntegrator()).thenReturn(integrator);
        when(core.getConfig()).thenReturn(new Configuration());
        doReturn(bridge).when(core).getPlugin();
        AuthMeApi api = mock(AuthMeApi.class, withSettings().extraInterfaces(PreJoinApi.class));
        source.row = null;
        playerEntries.clear();
        pendingEntries.clear();
        verifiedEntries.clear();
        when(api.isRegistered(NAME)).thenAnswer(call -> source.row != null);
        when(api.registerPlayer(eq(NAME), anyString())).thenAnswer(call -> {
            source.row = new Row();
            return true;
        });
        doAnswer(call -> {
            if (replaced) { source.row = new Row(); }
            throw new IllegalStateException(HASH);
        }).when((PreJoinApi) api).forceLoginFromProxy(NAME);
        try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
            singleton.when(AuthMeApi::getInstance).thenReturn(api);
            FloodgatePreLoginAuth.Outcome outcome = new FloodgatePreLoginAuth(core).handle(NAME,
                    new BedrockSession(false, "true", "true", "true"), new AuthMeHook(bridge));
            assertEquals(replaced ? FloodgatePreLoginAuth.Outcome.REGISTERED_LOGIN_CLEANUP_FAILED
                    : FloodgatePreLoginAuth.Outcome.REGISTERED_LOGIN_FAILED, outcome);
        }
        assertEquals(replaced ? 0 : 1, source.deletes);
        assertEquals(replaced, source.row != null);
        verify(api, never()).forceUnregister(anyString());
    }

    @Test
    void emptyPreJoinCachesAllowVerifiedDeletion() {
        playerEntries.clear();
        pendingEntries.clear();
        verifiedEntries.clear();
        assertTrue(integrator.deleteRegistration(capture()));
        assertNull(source.row);
    }

    enum ChangedField { NAME, HASH, SALT, UUID, REGISTRATION_DATE, PREMIUM_UUID, LAST_LOGIN }

    @ParameterizedTest
    @EnumSource(ChangedField.class)
    void anyChangedFingerprintFieldPreventsDeletionAndCacheMutation(ChangedField field) {
        Registration registration = capture();
        switch (field) {
            case NAME: source.row.name = "another"; break;
            case HASH: source.row.hash = "changed"; break;
            case SALT: source.row.salt = "changed"; break;
            case UUID: source.row.uuid = UUID.randomUUID(); break;
            case REGISTRATION_DATE: source.row.date++; break;
            case PREMIUM_UUID: source.row.premiumUuid = UUID.randomUUID(); break;
            case LAST_LOGIN: source.row.lastLogin = 42L; break;
            default: throw new AssertionError(field);
        }
        assertFalse(integrator.deleteRegistration(registration));
        assertEquals(0, source.deletes);
        assertEquals(Arrays.asList("read"), source.operations);
        assertEquals(1, playerEntries.size());
        assertEquals(1, pendingEntries.size());
        assertEquals(1, verifiedEntries.size());
    }

    @Test
    void anAbsentRowIsNotVerifiedDeletion() {
        Registration registration = capture();
        source.row = null;
        assertFalse(integrator.deleteRegistration(registration));
        assertEquals(0, source.deletes);
        assertEquals(1, playerEntries.size());
    }

    @Test
    void absentOrUnusableCaptureNeverProducesASnapshot() {
        source.row = null;
        assertNull(integrator.captureRegistration(NAME));
        source.row = new Row();
        source.row.hash = "";
        assertNull(integrator.captureRegistration(NAME));
        source.row.hash = null;
        assertNull(integrator.captureRegistration(NAME));
        assertFalse(integrator.deleteRegistration(null));
        assertEquals(0, source.deletes);
    }

    @Test
    void missingSnapshotFieldsWrongNameOrUnknownDatePreventCapture() throws Exception {
        source.row.name = "another";
        assertNull(integrator.captureRegistration(NAME));
        source.row.name = NAME;
        source.row.date = 0;
        assertNull(integrator.captureRegistration(NAME));
        set("dataSource", new ReadOnlySource(new Object()));
        assertNull(integrator.captureRegistration(NAME));
        assertEquals(0, source.deletes);
    }

    @Test
    void nullOptionalIdentityAndSaltFieldsAreStable() {
        source.row.uuid = null;
        source.row.premiumUuid = null;
        source.row.salt = null;
        source.row.lastLogin = null;
        assertTrue(integrator.deleteRegistration(capture()));
    }

    @Test
    void aMatchingPremiumUuidIsComparedRatherThanIgnored() {
        source.row.premiumUuid = UUID.randomUUID();
        assertTrue(integrator.deleteRegistration(capture()));
    }

    @Test
    void readFailureDuringCaptureOrComparisonNeverDeletesOrLeaksSecrets() {
        source.failRead = true;
        assertNull(integrator.captureRegistration(NAME));
        source.failRead = false;
        Registration registration = capture();
        source.failRead = true;
        assertFalse(integrator.deleteRegistration(registration));
        assertEquals(0, source.deletes);
        assertSafeLogs(registration);
    }

    @Test
    void falseRemovalIsFailureEvenWithAbsentReadback() {
        Registration registration = capture();
        source.removeResult = false;
        assertFalse(integrator.deleteRegistration(registration));
        assertNull(source.row);
        assertEquals(Arrays.asList("read", "remove", "read"), source.operations);
        assertEquals(1, playerEntries.size());
    }

    @Test
    void falseRemovalWithRemainingRowDoesNotClearCaches() {
        Registration registration = capture();
        source.removeResult = false;
        source.keepRow = true;
        assertFalse(integrator.deleteRegistration(registration));
        assertNotNull(source.row);
        assertEquals(1, playerEntries.size());
    }

    @Test
    void removeExceptionDoesNotBecomeSuccessOrLeakSecrets() {
        Registration registration = capture();
        source.failRemove = true;
        assertFalse(integrator.deleteRegistration(registration));
        assertNotNull(source.row);
        assertEquals(1, playerEntries.size());
        assertSafeLogs(registration);
    }

    @Test
    void aTrueRemoveWithPresentReadbackIsFailure() {
        Registration registration = capture();
        source.keepRow = true;
        assertFalse(integrator.deleteRegistration(registration));
        assertNotNull(source.row);
        assertEquals(Arrays.asList("read", "remove", "read"), source.operations);
    }

    @Test
    void aReadbackExceptionAfterRemovalIsStillFailure() {
        Registration registration = capture();
        source.failReadAt = source.reads + 2;
        assertFalse(integrator.deleteRegistration(registration));
        assertNull(source.row);
        assertSafeLogs(registration);
    }

    @Test
    void missingDataSourceOrRemoveBoundaryNeverFallsBackToAsyncApi() throws Exception {
        set("dataSource", new Object());
        assertNull(integrator.captureRegistration(NAME));
        ReadOnlySource readOnly = new ReadOnlySource(source.row);
        set("dataSource", readOnly);
        Registration registration = integrator.captureRegistration(NAME);
        assertNotNull(registration);
        try (MockedStatic<AuthMeApi> api = mockStatic(AuthMeApi.class)) {
            assertFalse(integrator.deleteRegistration(registration));
            api.verifyNoInteractions();
        }
        assertEquals(0, source.deletes);
    }

    enum Cache { PLAYER, PENDING, VERIFIED }

    @ParameterizedTest
    @EnumSource(Cache.class)
    void unavailableCacheBoundaryIsDetectedBeforeDeleting(Cache cache) throws Exception {
        Registration registration = capture();
        set(cache == Cache.PLAYER ? "playerCache"
                : cache == Cache.PENDING ? "pendingPremiumCache" : "premiumLoginVerifier", new Object());
        assertFalse(integrator.deleteRegistration(registration));
        assertEquals(0, source.deletes);
        assertNotNull(source.row);
    }

    @ParameterizedTest
    @EnumSource(Cache.class)
    void anUnreviewedConcurrentMapCannotAuthorizeDeletion(Cache cache) {
        Registration registration = capture();
        Object map = new java.util.concurrent.ConcurrentSkipListMap<String, Object>();
        if (cache == Cache.PLAYER) { players.cache = map; }
        if (cache == Cache.PENDING) { pending.pending = map; }
        if (cache == Cache.VERIFIED) { verifier.verified = map; }
        assertFalse(integrator.deleteRegistration(registration));
        assertEquals(0, source.deletes);
        assertNotNull(source.row);
    }

    @Test
    void aDifferentAuthenticatedCacheRowBlocksDeletion() {
        Registration registration = capture();
        Row other = new Row();
        other.hash = "someone else's hash";
        playerEntries.put(NAME, other);
        assertFalse(integrator.deleteRegistration(registration));
        assertEquals(0, source.deletes);
        assertSame(other, playerEntries.get(NAME));
    }

    @ParameterizedTest
    @EnumSource(Cache.class)
    void cacheEntriesReplacedDuringDeletionArePreservedAndReportedAsFailure(Cache cache) {
        Registration registration = capture();
        Object replacement = new Row();
        ConcurrentMap<String, Object> entries = cache == Cache.PLAYER ? playerEntries
                : cache == Cache.PENDING ? pendingEntries : verifiedEntries;
        source.afterRemove = () -> entries.put(NAME, replacement);
        assertFalse(integrator.deleteRegistration(registration));
        assertSame(replacement, entries.get(NAME));
        assertNull(source.row);
    }

    @ParameterizedTest
    @EnumSource(Cache.class)
    void newlyAddedReconnectCacheEntriesAreNotCleared(Cache cache) {
        ConcurrentMap<String, Object> entries = cache == Cache.PLAYER ? playerEntries
                : cache == Cache.PENDING ? pendingEntries : verifiedEntries;
        entries.clear();
        Registration registration = capture();
        Object replacement = new Row();
        source.afterRemove = () -> entries.put(NAME, replacement);
        assertFalse(integrator.deleteRegistration(registration));
        assertSame(replacement, entries.get(NAME));
    }

    @Test
    void cacheClearExceptionAfterRemovalIsNotReportedAsSuccess() {
        Registration registration = capture();
        players.cache = new ConcurrentHashMap<String, Object>() {
            @Override
            public Object compute(String key, BiFunction<? super String, ? super Object, ?> function) {
                throw new IllegalStateException("DO-NOT-LOG-HASH");
            }
        };
        assertFalse(integrator.deleteRegistration(registration));
        assertNull(source.row);
        assertSafeLogs(registration);
    }

    @Test
    void authMe5RemainsUnsupportedWithoutAnyReadOrWrite() {
        when(detector.isAuthMe6()).thenReturn(false);
        assertNull(integrator.captureRegistration(NAME));
        assertFalse(integrator.deleteRegistration(new Registration(NAME, HASH, SALT, null, 1234L, null, 0L)));
        assertTrue(source.operations.isEmpty());
    }

    @Test
    void sameNameCaseVariantsSerializeAndDeleteAtMostOnce() throws Exception {
        Registration firstSnapshot = capture();
        Registration secondSnapshot = integrator.captureRegistration("BEDROCK");
        source.deletionEntered = new CountDownLatch(1);
        source.releaseDeletion = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> integrator.deleteRegistration(firstSnapshot));
            assertTrue(source.deletionEntered.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() -> {
                secondStarted.countDown();
                return integrator.deleteRegistration(secondSnapshot);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            source.releaseDeletion.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertFalse(second.get(5, TimeUnit.SECONDS));
            assertEquals(1, source.deletes);
        } finally {
            source.releaseDeletion.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void cleanupImplementationAndTestsRemainExactFoliaMirrors() throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("bukkit"))) { root = root.getParent(); }
        for (String file : Arrays.asList(
                "src/main/java/com/github/games647/fastlogin/bukkit/compat/AuthMePremiumIntegrator.java",
                "src/main/java/com/github/games647/fastlogin/bukkit/compat/AuthMeRegistrationCleanup.java",
                "src/test/java/com/github/games647/fastlogin/bukkit/compat/AuthMeRegistrationCleanupTest.java")) {
            assertEquals(new String(Files.readAllBytes(root.resolve("bukkit").resolve(file)), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(root.resolve("folia").resolve(file)), StandardCharsets.UTF_8));
        }
    }

    private Registration capture() {
        Registration registration = integrator.captureRegistration("Bedrock");
        assertNotNull(registration);
        assertEquals(NAME, registration.getPlayerName());
        source.operations.clear();
        return registration;
    }

    private void set(String name, Object value) throws Exception {
        Field field = AuthMePremiumIntegrator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(integrator, value);
    }

    private void assertSafeLogs(Registration registration) {
        assertFalse(registration.toString().contains(HASH));
        assertFalse(registration.toString().contains(SALT));
        mockingDetails(logger).getInvocations().forEach(invocation -> {
            for (Object argument : invocation.getArguments()) {
                assertFalse(argument instanceof Throwable);
                assertFalse(argument instanceof Registration);
                assertFalse(String.valueOf(argument).contains(HASH));
                assertFalse(String.valueOf(argument).contains(SALT));
            }
        });
    }

    public static final class Source {
        Row row = new Row();
        int deletes;
        int reads;
        int failReadAt;
        boolean failRead;
        boolean failRemove;
        boolean keepRow;
        boolean removeResult = true;
        Thread expectedThread;
        Runnable afterRemove;
        CountDownLatch deletionEntered;
        CountDownLatch releaseDeletion;
        final java.util.List<String> operations = new CopyOnWriteArrayList<>();
        public Row getAuth(String name) {
            checkBoundary(name);
            operations.add("read");
            reads++;
            if (failRead || reads == failReadAt) { throw new IllegalStateException(HASH); }
            return row;
        }
        public boolean removeAuth(String name) throws InterruptedException {
            checkBoundary(name);
            operations.add("remove");
            deletes++;
            if (deletionEntered != null) {
                deletionEntered.countDown();
                if (!releaseDeletion.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("timeout"); }
            }
            if (failRemove) { throw new IllegalStateException(HASH); }
            if (!keepRow) { row = null; }
            if (afterRemove != null) { afterRemove.run(); }
            return removeResult;
        }
        private void checkBoundary(String name) {
            assertEquals(NAME, name);
            assertTrue(Thread.holdsLock(this), "DataSource operations must share the outer monitor");
            if (expectedThread != null) { assertSame(expectedThread, Thread.currentThread()); }
        }
    }

    public static final class ReadOnlySource {
        private final Object row;
        ReadOnlySource(Object row) { this.row = row; }
        public Object getAuth(String name) { return row; }
    }

    public static final class Row {
        String name = NAME;
        String hash = HASH;
        String salt = SALT;
        UUID uuid = UUID.randomUUID();
        UUID premiumUuid;
        long date = 1234L;
        Long lastLogin = 0L;
        public String getNickname() { return name; }
        public Password getPassword() { return new Password(hash, salt); }
        public UUID getUuid() { return uuid; }
        public UUID getPremiumUuid() { return premiumUuid; }
        public long getRegistrationDate() { return date; }
        public Long getLastLogin() { return lastLogin; }
    }

    public static final class Password {
        private final String hash;
        private final String salt;
        Password(String hash, String salt) { this.hash = hash; this.salt = salt; }
        public String getHash() { return hash; }
        public String getSalt() { return salt; }
    }

    public static final class Players {
        Object cache;
        public Object getCache() { return cache; }
    }

    public static final class Pending { private Object pending; }
    public static final class Verifier { private Object verified; }
}
