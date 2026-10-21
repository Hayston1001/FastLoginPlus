/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.storage;

import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * the upsert insert path, the row-id backfill of the update branch
 * and the name-level striped lock that closes concurrent load-modify-save
 * windows.  Runs against a real SQLite database (xerial driver in test scope).
 */
class SQLStorageUpsertLockTest {

    @TempDir
    Path tempDir;

    private PlatformPlugin<?> plugin;
    private SQLiteStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(PlatformPlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getName()).thenReturn("test");
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getPluginFolder()).thenReturn(tempDir);

        storage = new SQLiteStorage(plugin, "{pluginDir}/FastLogin.db", new HikariConfig());
        storage.createTables();
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    private StoredProfile newProfile(String name, boolean premium, String lastIp) {
        // rowId -1 -> isExistingPlayer() == false -> insert (upsert) path
        return new StoredProfile(null, name, premium, FloodgateState.FALSE, lastIp);
    }

    private int countRows(String name) throws Exception {
        String dbPath = tempDir.resolve("FastLogin.db").toString();
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM `premium` WHERE lower(`Name`) = lower('" + name + "')")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-uuid", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"})
    void malformedOrNullUuidPreservesTheProfileAndDatabaseValue(String storedUuid) throws Exception {
        String dbPath = tempDir.resolve("FastLogin.db").toString();
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = con.prepareStatement("INSERT INTO premium "
                     + "(UUID, Name, Premium, Floodgate, SameNameCracked, LastIp) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, storedUuid);
            stmt.setString(2, "Steve");
            stmt.setBoolean(3, true);
            stmt.setInt(4, FloodgateState.FALSE.getValue());
            stmt.setBoolean(5, true);
            stmt.setString(6, "1.2.3.4");
            stmt.executeUpdate();
        }

        StoredProfile loaded = storage.loadProfile("Steve");
        assertNotNull(loaded);
        assertTrue(loaded.isExistingPlayer());
        assertNull(loaded.getId());
        assertEquals("Steve", loaded.getName());
        assertTrue(loaded.isOnlinemodePreferred());
        assertTrue(loaded.isSameNameCracked());
        assertEquals("1.2.3.4", loaded.getLastIp());
        verify(plugin.getLog(), times(storedUuid == null ? 0 : 1)).warn(
                "Invalid UUID column for player {} (UserID {}): using no UUID; database row unchanged",
                "Steve", loaded.getRowId());

        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT UUID, Premium FROM premium WHERE Name = 'Steve'")) {
            assertTrue(rs.next());
            assertEquals(storedUuid, rs.getString("UUID"), "loading must not repair or clear the stored value");
            assertTrue(rs.getBoolean("Premium"));
            assertFalse(rs.next());
        }
    }

    @Test
    void upsertSameNameKeepsSingleRowWithLatestValues() throws Exception {
        StoredProfile first = newProfile("Steve", false, "1.1.1.1");
        assertTrue(storage.saveQuietly(first));
        assertTrue(first.getRowId() > 0, "plain insert must backfill the row id");

        // a second first-time save for the same name (e.g. two backends or two
        // racing tasks): previously the UNIQUE(Name) constraint made this insert
        // fail and save() swallowed the exception, losing the profile
        StoredProfile second = newProfile("Steve", true, "2.2.2.2");
        assertTrue(storage.saveQuietly(second));

        assertEquals(1, countRows("Steve"), "upsert must not create a second row");

        StoredProfile loaded = storage.loadProfile("Steve");
        assertNotNull(loaded);
        assertTrue(loaded.isOnlinemodePreferred(), "latest premium flag must win");
        assertEquals("2.2.2.2", loaded.getLastIp(), "latest LastIp must win");
    }

    @Test
    void upsertUpdateBranchBackfillsRowId() {
        storage.saveQuietly(newProfile("Steve", false, "1.1.1.1"));

        // this upsert takes the ON CONFLICT DO UPDATE branch where
        // getGeneratedKeys() is driver-dependent; the SELECT fallback must
        // still fill in a usable row id so the profile stays saveable
        StoredProfile second = newProfile("Steve", true, "2.2.2.2");
        assertTrue(storage.saveQuietly(second));

        assertTrue(second.isExistingPlayer(), "upsert update branch must mark the profile existing");
        assertNotEquals(0, second.getRowId(), "row id must be backfilled");

        // the backfilled id must point at the real row (a follow-up UPDATE save works)
        second.setLastIp("3.3.3.3");
        assertTrue(storage.saveQuietly(second));
        StoredProfile loaded = storage.loadProfile("Steve");
        assertEquals("3.3.3.3", loaded.getLastIp());
    }

    @Test
    void unlockedWindowsLoseUpdates() {
        // discriminator: WITHOUT the name lock two interleaved load-modify-save
        // windows on the same row lose the first write
        storage.saveQuietly(newProfile("Steve", false, "1.1.1.1"));

        StoredProfile windowA = storage.loadProfile("Steve");
        StoredProfile windowB = storage.loadProfile("Steve");
        assertNotNull(windowA);
        assertNotNull(windowB);

        windowA.setOnlinemodePreferred(true);
        storage.saveQuietly(windowA);

        windowB.setLastIp("9.9.9.9");
        storage.saveQuietly(windowB);

        StoredProfile after = storage.loadProfile("Steve");
        assertFalse(after.isOnlinemodePreferred(),
                "without the lock the premium write of window A is silently lost");
    }

    @Test
    void lockedWindowsKeepAllUpdates() throws Exception {
        // with the name lock the same two windows serialize: B's load happens
        // after A's save, so BOTH modifications land
        storage.saveQuietly(newProfile("Steve", false, "1.1.1.1"));

        CountDownLatch aInside = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        AtomicBoolean aResult = new AtomicBoolean(false);
        AtomicBoolean bResult = new AtomicBoolean(false);

        // window A: holds the stripe while paused, then flips premium
        Thread threadA = new Thread(() -> {
            aResult.set(storage.withNameLock("Steve", () -> {
                StoredProfile profile = storage.loadProfile("Steve");
                aInside.countDown();
                try {
                    releaseA.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                profile.setOnlinemodePreferred(true);
                return storage.saveQuietly(profile);
            }));
        });

        // window B: waits on the stripe until A's window is fully done
        Thread threadB = new Thread(() -> {
            bResult.set(storage.withNameLock("Steve", () -> {
                StoredProfile profile = storage.loadProfile("Steve");
                profile.setLastIp("9.9.9.9");
                return storage.saveQuietly(profile);
            }));
        });

        threadA.start();
        assertTrue(aInside.await(5, TimeUnit.SECONDS), "A must enter its window");

        threadB.start();
        Thread.sleep(200); // give B a chance to (wrongly) run through if unlocked
        releaseA.countDown();

        threadA.join(5_000);
        threadB.join(5_000);
        assertTrue(aResult.get(), "window A must complete");
        assertTrue(bResult.get(), "window B must complete");

        StoredProfile after = storage.loadProfile("Steve");
        assertTrue(after.isOnlinemodePreferred(), "A's premium flip must survive");
        assertEquals("9.9.9.9", after.getLastIp(), "B's LastIp write must land");
    }

    @Test
    void concurrentFirstSavesProduceSingleRow() throws Exception {
        // two threads save a NEW profile for the same name at the
        // same time — the upsert must collapse this into exactly one row
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicBoolean firstOk = new AtomicBoolean(false);
        AtomicBoolean secondOk = new AtomicBoolean(false);

        Thread threadA = new Thread(() -> {
            StoredProfile profile = newProfile("Steve", true, "1.1.1.1");
            await(barrier);
            firstOk.set(storage.saveQuietly(profile) && profile.getRowId() > 0);
        });
        Thread threadB = new Thread(() -> {
            StoredProfile profile = newProfile("Steve", false, "2.2.2.2");
            await(barrier);
            secondOk.set(storage.saveQuietly(profile) && profile.getRowId() > 0);
        });

        threadA.start();
        threadB.start();
        threadA.join(5_000);
        threadB.join(5_000);

        assertTrue(firstOk.get() || secondOk.get(), "at least one save must fully succeed");
        assertEquals(1, countRows("Steve"), "concurrent first saves must collapse to one row");

        // both windows must end up with a usable row id (backfill on update branch)
        StoredProfile loaded = storage.loadProfile("Steve");
        assertNotNull(loaded);
        assertNotEquals(0, loaded.getRowId());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
