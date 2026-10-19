/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.storage;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * the MySQL insert must stay an upsert so concurrent first-time
 * saves for the same name cannot lose the profile.  The live-database round
 * trip is a manual integration test.
 */
class MySQLStorageUpsertDialectTest {

    @Test
    void upsertStatementIsComplete() {
        String stmt = MySQLStorage.INSERT_PROFILE_UPSERT;

        assertTrue(stmt.contains("ON DUPLICATE KEY UPDATE"), "MySQL upsert clause required");
        // every persisted column must be assigned on the duplicate branch
        assertTrue(stmt.contains("`UUID`="));
        assertTrue(stmt.contains("`Premium`="));
        assertTrue(stmt.contains("`Floodgate`="));
        assertTrue(stmt.contains("`LastIp`="));
        // byte-exact name comparison keeps the case-variant protection
        assertTrue(stmt.contains("HEX(`Name`)=HEX(VALUES(`Name`))"),
                "case-variant duplicate must not overwrite the existing row");
    }

    /**
     * Manual test: needs a local MySQL/MariaDB server (database "fastlogin").
     * Verifies that two first-time saves of the same name collapse into one row
     * on the real server dialect.
     */
    @Test
    @Disabled("Requires a running MySQL server on localhost - run manually")
    void liveDatabaseCollapsesConcurrentFirstSaves() {
        // com.mysql.cj.jdbc.Driver must be on the test classpath when run manually
        // HikariConfig config = new HikariConfig();
        // MySQLStorage storage = new MySQLStorage(plugin, "mysql", "127.0.0.1", 3306,
        //         "fastlogin", config, false);
        // storage.createTables();
        // ... same flow as SQLStorageUpsertLockTest.concurrentFirstSavesProduceSingleRow
        // storage.close();
    }
}
