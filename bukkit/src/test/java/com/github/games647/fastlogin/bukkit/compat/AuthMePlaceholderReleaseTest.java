/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.compat;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator.ReleaseOutcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthMePlaceholderReleaseTest {
    private final UUID owner = UUID.randomUUID();
    private final Logger logger = mock(Logger.class);
    private final AuthMeVersionDetector detector = mock(AuthMeVersionDetector.class);
    private final DataSource source = new DataSource();
    private final Pending pending = new Pending();
    private final Verifier verifier = new Verifier();
    private AuthMePremiumIntegrator integrator;

    @BeforeEach
    void setUp() throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        when(plugin.getLog()).thenReturn(logger);
        when(detector.isAuthMe6()).thenReturn(true);
        integrator = new AuthMePremiumIntegrator(plugin, detector);
        set("dataSource", source);
        set("pendingPremiumCache", pending);
        set("premiumLoginVerifier", verifier);
        source.row = new Row(new Password(""), owner);
        pending.names.add("testuser");
        verifier.verified.put("testuser", owner);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void emptyOrNullHashReleasesSynchronouslyWithoutCommandOrScheduler(String hash) {
        source.row = new Row(new Password(hash), owner);
        source.expectedThread = Thread.currentThread();
        try (MockedStatic<AuthMeApi> api = mockStatic(AuthMeApi.class);
                MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertEquals(RELEASED, release());
            assertNull(source.row);
            assertFalse(pending.names.contains("testuser"));
            assertFalse(verifier.verified.containsKey("testuser"));
            assertEquals(Arrays.asList("read", "remove", "read"), source.operations);
            api.verifyNoInteractions();
            bukkit.verifyNoInteractions();
        }
        assertAudit("RELEASED");
    }

    @Test
    void nullPasswordObjectIsUnusable() {
        source.row = new Row(null, owner);
        assertEquals(RELEASED, release());
    }

    @Test
    void legacyRowWithoutPremiumUuidMayBeReleased() {
        source.row = new Row(new Password(""), null);
        assertEquals(RELEASED, release());
        assertAudit("RELEASED");
    }

    @Test
    void passwordProtectsEvenAMatchingPremiumRowAndIsNeverLogged() {
        source.row = new Row(new Password("DO-NOT-LOG-SECRET"), owner);
        assertEquals(NOT_APPLICABLE, release());
        assertEquals(0, source.deletes.get());
        assertAudit("NOT_APPLICABLE");
    }

    @Test
    void passwordWithDifferentOwnerIsNotAnActiveConflict() {
        source.row = new Row(new Password("DO-NOT-LOG-SECRET"), UUID.randomUUID());
        Row original = source.row;
        assertEquals(NOT_APPLICABLE, release());
        assertSame(original, source.row);
        assertEquals(0, source.deletes.get());
        assertAudit("NOT_APPLICABLE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void differentOwnerWithUnusableHashReportsMismatchWithoutChangingState(String hash) {
        assertOwnershipMismatch(new Row(new Password(hash), UUID.randomUUID()));
    }

    @Test
    void differentOwnerWithAbsentPasswordReportsMismatchWithoutChangingState() {
        assertOwnershipMismatch(new Row(null, UUID.randomUUID()));
    }

    private void assertOwnershipMismatch(Row original) {
        source.row = original;
        assertEquals(UUID_MISMATCH, release());
        assertSame(original, source.row);
        assertEquals(0, source.deletes.get());
        assertEquals(Arrays.asList("read"), source.operations);
        assertTrue(pending.names.contains("testuser"));
        assertEquals(owner, verifier.verified.get("testuser"));
        verify(logger).info(contains("AuthMe placeholder release:"),
                eq("TestUser"), eq(owner), eq(original.getPremiumUuid()), eq("UUID_MISMATCH"));
        assertAudit("UUID_MISMATCH");
    }

    @Test
    void missingVerifiedUuidDoesNotReadOrDelete() {
        source.row = new Row(new Password(""), UUID.randomUUID());
        assertEquals(NOT_APPLICABLE, integrator.releaseUnusablePlaceholder("TestUser", null));
        assertTrue(source.operations.isEmpty());
    }

    @Test
    void absentRowIsSuccessfulWithoutDeletion() {
        source.row = null;
        assertEquals(ALREADY_ABSENT, release());
        assertEquals(0, source.deletes.get());
        assertAudit("ALREADY_ABSENT");
    }

    @Test
    void falseDeleteWithAbsentReadbackIsAlreadyAbsent() {
        source.removeResult = false;
        assertEquals(ALREADY_ABSENT, release());
        assertNull(source.row);
    }

    @Test
    void rowRemainingAfterDeleteIsFailure() {
        source.keepRow = true;
        assertEquals(FAILED, release());
        assertNotNull(source.row);
        assertEquals(Arrays.asList("read", "remove", "read"), source.operations);
        assertAudit("FAILED");
    }

    @Test
    void deleteExceptionDoesNotEscapeOrLeakSecrets() {
        source.failDelete = true;
        assertEquals(FAILED, release());
        assertNotNull(source.row);
        assertAudit("FAILED");
    }

    @Test
    void readExceptionDoesNotDelete() {
        source.failRead = true;
        assertEquals(FAILED, release());
        assertEquals(0, source.deletes.get());
        assertAudit("FAILED");
    }

    @Test
    void unsupportedVersionLogsOnceWithoutReading() {
        when(detector.isAuthMe6()).thenReturn(false);
        assertEquals(UNSUPPORTED, release());
        assertEquals(UNSUPPORTED, release());
        verify(logger, times(1)).info(contains("AuthMe 6 is required"));
        assertTrue(source.operations.isEmpty());
    }

    @Test
    void missingReflectionHandleDoesNotFallBackToAsyncApi() throws Exception {
        set("dataSource", new Object());
        try (MockedStatic<AuthMeApi> api = mockStatic(AuthMeApi.class)) {
            assertEquals(UNSUPPORTED, release());
            api.verifyNoInteractions();
        }
    }

    @Test
    void unavailableCacheIsDetectedBeforeDeleting() throws Exception {
        set("pendingPremiumCache", new Object());
        assertEquals(UNSUPPORTED, release());
        assertEquals(0, source.deletes.get());
        assertNotNull(source.row);
    }

    @Test
    void concurrentCaseInsensitiveNamesDeleteAtMostOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        source.deletionEntered = new CountDownLatch(1);
        source.releaseDeletion = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<AuthMePremiumIntegrator.ReleaseOutcome> first = executor.submit(
                    () -> integrator.releaseUnusablePlaceholder("TestUser", owner));
            assertTrue(source.deletionEntered.await(5, TimeUnit.SECONDS));
            Future<AuthMePremiumIntegrator.ReleaseOutcome> second = executor.submit(() -> {
                secondStarted.countDown();
                return integrator.releaseUnusablePlaceholder("TESTUSER", owner);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            assertEquals(Arrays.asList("read", "remove"), source.operations);
            source.releaseDeletion.countDown();
            assertEquals(new HashSet<>(Arrays.asList(RELEASED, ALREADY_ABSENT)),
                    new HashSet<>(Arrays.asList(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))));
            assertEquals(1, source.deletes.get());
        } finally {
            source.releaseDeletion.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void foliaIntegratorAndReleaseTestsAreExactMirrors() throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("bukkit"))) { root = root.getParent(); }
        String main = "src/main/java/com/github/games647/fastlogin/bukkit/compat/AuthMePremiumIntegrator.java";
        String test = "src/test/java/com/github/games647/fastlogin/bukkit/compat/AuthMePlaceholderReleaseTest.java";
        for (String file : Arrays.asList(main, test)) {
            assertEquals(new String(Files.readAllBytes(root.resolve("bukkit").resolve(file)), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(root.resolve("folia").resolve(file)), StandardCharsets.UTF_8));
        }
    }

    private AuthMePremiumIntegrator.ReleaseOutcome release() {
        return integrator.releaseUnusablePlaceholder("TestUser", owner);
    }

    private void set(String name, Object value) throws Exception {
        Field field = AuthMePremiumIntegrator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(integrator, value);
    }

    private void assertAudit(String result) {
        verify(logger).info(contains("reason=auto-login policy disabled and no usable password"),
                eq("TestUser"), eq(owner), nullable(UUID.class), eq(result));
        mockingDetails(logger).getInvocations().forEach(invocation -> {
            for (Object argument : invocation.getArguments()) {
                assertFalse(argument instanceof Throwable);
                assertFalse(String.valueOf(argument).contains("DO-NOT-LOG-SECRET"));
            }
        });
    }

    public static final class DataSource {
        volatile Row row;
        final AtomicInteger deletes = new AtomicInteger();
        final java.util.List<String> operations = new java.util.concurrent.CopyOnWriteArrayList<>();
        boolean removeResult = true;
        boolean keepRow;
        boolean failDelete;
        boolean failRead;
        Thread expectedThread;
        CountDownLatch deletionEntered;
        CountDownLatch releaseDeletion;
        public Row getAuth(String name) {
            checkThread(name);
            operations.add("read");
            if (failRead) { throw new IllegalStateException("DO-NOT-LOG-SECRET"); }
            return row;
        }
        public boolean removeAuth(String name) {
            checkThread(name);
            operations.add("remove");
            deletes.incrementAndGet();
            if (deletionEntered != null) {
                deletionEntered.countDown();
                try {
                    if (!releaseDeletion.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("timeout"); }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }
            if (failDelete) { throw new IllegalStateException("DO-NOT-LOG-SECRET"); }
            if (!keepRow) { row = null; }
            return removeResult;
        }
        private void checkThread(String name) {
            assertEquals("testuser", name);
            if (expectedThread != null) { assertSame(expectedThread, Thread.currentThread()); }
        }
    }

    public static final class Row {
        private final Password password;
        private final UUID uuid;
        Row(Password password, UUID uuid) { this.password = password; this.uuid = uuid; }
        public Password getPassword() { return password; }
        public UUID getPremiumUuid() { return uuid; }
    }

    public static final class Password {
        private final String hash;
        Password(String hash) { this.hash = hash; }
        public String getHash() { return hash; }
    }

    public static final class Pending {
        final java.util.Set<String> names = ConcurrentHashMap.newKeySet();
        public void removePending(String name) { names.remove(name); }
    }

    public static final class Verifier {
        private final Map<String, UUID> verified = new ConcurrentHashMap<>();
    }
}
