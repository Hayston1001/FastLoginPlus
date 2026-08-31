/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.core.storage;

import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SQLiteStorageTest {

    @TempDir
    Path tempDir;

    private PlatformPlugin<?> plugin;
    private Logger logger;
    private SQLiteStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(PlatformPlugin.class);
        logger = mock(Logger.class);
        when(plugin.getName()).thenReturn("test");
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getPluginFolder()).thenReturn(tempDir);

        storage = openStorage();
        storage.createTables();
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    private SQLiteStorage openStorage() {
        return new SQLiteStorage(plugin, "{pluginDir}/FastLogin.db", new HikariConfig());
    }

    @Test
    void loadsProfileCaseInsensitive() {
        StoredProfile profile = new StoredProfile(null, "Steve", true, FloodgateState.FALSE, "127.0.0.1");
        storage.save(profile);

        // "Steve" and "steve" are the same Minecraft account: lookup must hit the same row
        StoredProfile loaded = storage.loadProfile("steve");

        assertNotNull(loaded);
        assertEquals(profile.getRowId(), loaded.getRowId());
        assertTrue(loaded.isOnlinemodePreferred());
        assertEquals("Steve", loaded.getName());
    }

    @Test
    void rejectsCaseVariantDuplicate() {
        storage.save(new StoredProfile(null, "Steve", true, FloodgateState.FALSE, "127.0.0.1"));

        // A second row differing only by case would split the player into premium + cracked
        // identities. UNIQUE(Name) with COLLATE NOCASE must reject the insert.
        storage.save(new StoredProfile(null, "steve", false, FloodgateState.FALSE, "127.0.0.1"));

        StoredProfile loaded = storage.loadProfile("steve");
        assertNotNull(loaded);
        assertEquals("Steve", loaded.getName());
        assertTrue(loaded.isOnlinemodePreferred());
    }

    @Test
    void migratesLegacyCaseSensitiveTable() throws Exception {
        storage.close();
        storage = null;

        // Remove the database created in setUp so we can simulate a pre-collation schema
        String dbPath = tempDir.resolve("FastLogin.db").toString();
        Files.deleteIfExists(Paths.get(dbPath));
        Files.deleteIfExists(Paths.get(dbPath + "-wal"));
        Files.deleteIfExists(Paths.get(dbPath + "-shm"));

        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = con.createStatement()) {
            stmt.execute("CREATE TABLE `premium` ("
                    + "`UserID` INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "`UUID` CHAR(36), "
                    + "`Name` VARCHAR(16) NOT NULL, "
                    + "`Premium` BOOLEAN NOT NULL, "
                    + "`LastIp` VARCHAR(255) NOT NULL, "
                    + "`LastLogin` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "`Floodgate` INTEGER(3), "
                    + "UNIQUE (`Name`))");
            stmt.execute("INSERT INTO `premium` (`UUID`, `Name`, `Premium`, `LastIp`, `Floodgate`) "
                    + "VALUES (NULL, 'Steve', 1, '127.0.0.1', 0)");
        }

        storage = openStorage();
        storage.createTables();

        // Data must survive the rebuild, and lookups must now be case-insensitive
        StoredProfile loaded = storage.loadProfile("steve");
        assertNotNull(loaded);
        assertEquals("Steve", loaded.getName());
        assertTrue(loaded.isOnlinemodePreferred());

        // Migration must be logged: start (info) and completion (info)
        verify(logger).info("Migrating premium table to case-insensitive Name collation (COLLATE NOCASE)");
        verify(logger).info("Migrated premium table to case-insensitive Name collation (COLLATE NOCASE)");
    }

    @Test
    void migrationIsIdempotent() throws Exception {
        // Subsequent startup calls must not rebuild the table again (or lose data)
        storage.createTables();
        storage.createTables();

        storage.save(new StoredProfile(null, "Steve", true, FloodgateState.FALSE, "127.0.0.1"));
        assertNotNull(storage.loadProfile("steve"));
    }

    @Test
    void migratesTableWithoutFloodgateColumn() throws Exception {
        // Even older databases predate the Floodgate column; createTables() must add it
        // (ADD_FLOODGATE_COLUMN_STMT) before the migration copies rows.
        storage.close();
        storage = null;

        String dbPath = tempDir.resolve("FastLogin.db").toString();
        Files.deleteIfExists(Paths.get(dbPath));
        Files.deleteIfExists(Paths.get(dbPath + "-wal"));
        Files.deleteIfExists(Paths.get(dbPath + "-shm"));

        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = con.createStatement()) {
            stmt.execute("CREATE TABLE `premium` ("
                    + "`UserID` INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "`UUID` CHAR(36), "
                    + "`Name` VARCHAR(16) NOT NULL, "
                    + "`Premium` BOOLEAN NOT NULL, "
                    + "`LastIp` VARCHAR(255) NOT NULL, "
                    + "`LastLogin` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE (`Name`))");
            stmt.execute("INSERT INTO `premium` (`UUID`, `Name`, `Premium`, `LastIp`) "
                    + "VALUES (NULL, 'Steve', 1, '127.0.0.1')");
        }

        storage = openStorage();
        storage.createTables();

        StoredProfile loaded = storage.loadProfile("steve");
        assertNotNull(loaded);
        assertEquals("Steve", loaded.getName());
        assertTrue(loaded.isOnlinemodePreferred());
        // Floodgate column must exist on the migrated table; unset rows are NOT_MIGRATED, not TRUE
        assertEquals(FloodgateState.NOT_MIGRATED, loaded.getFloodgate());
    }

    @Test
    void migrationDeduplicatesCaseVariants() throws Exception {
        // A case-sensitive database can legitimately hold "Steve" (premium) and "steve" (cracked)
        // as two rows. After migration only one row may survive — premium must win.
        storage.close();
        storage = null;

        String dbPath = tempDir.resolve("FastLogin.db").toString();
        Files.deleteIfExists(Paths.get(dbPath));
        Files.deleteIfExists(Paths.get(dbPath + "-wal"));
        Files.deleteIfExists(Paths.get(dbPath + "-shm"));

        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = con.createStatement()) {
            stmt.execute("CREATE TABLE `premium` ("
                    + "`UserID` INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "`UUID` CHAR(36), "
                    + "`Name` VARCHAR(16) NOT NULL, "
                    + "`Premium` BOOLEAN NOT NULL, "
                    + "`LastIp` VARCHAR(255) NOT NULL, "
                    + "`LastLogin` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "`Floodgate` INTEGER(3), "
                    + "UNIQUE (`Name`))");
            // Both rows may even carry the same premium UUID (the same player joined twice)
            stmt.execute("INSERT INTO `premium` (`UUID`, `Name`, `Premium`, `LastIp`, `Floodgate`) "
                    + "VALUES (NULL, 'Steve', 1, '1.1.1.1', 0)");
            stmt.execute("INSERT INTO `premium` (`UUID`, `Name`, `Premium`, `LastIp`, `Floodgate`) "
                    + "VALUES (NULL, 'steve', 0, '2.2.2.2', 0)");
        }

        storage = openStorage();
        storage.createTables();

        StoredProfile loaded = storage.loadProfile("steve");
        assertNotNull(loaded);
        assertEquals("Steve", loaded.getName());
        assertTrue(loaded.isOnlinemodePreferred());

        // Dropped rows must be reported: 2 case-variant rows -> 1 kept
        verify(logger).warn(anyString(), eq(1));
    }
}
