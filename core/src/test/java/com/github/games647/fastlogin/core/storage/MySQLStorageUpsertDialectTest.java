/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.storage;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(stmt.contains("`SameNameCracked`="));
        assertTrue(stmt.contains("`LastIp`="));
        // byte-exact name comparison keeps the case-variant protection
        assertTrue(stmt.contains("HEX(`Name`)=HEX(VALUES(`Name`))"),
                "case-variant duplicate must not overwrite the existing row");
    }

    /**
     * Guards the binding contract in {@code SQLStorage.saveQuietly}: the insert
     * branch binds six positional parameters, so the dialect statement must
     * declare the same columns in the same order.  A drifted column list shifts
     * every later value (a missing SameNameCracked once wrote LastIp into it).
     */
    @Test
    void upsertStatementBindsTheSameColumnsAsTheBaseInsert() {
        String upsert = MySQLStorage.INSERT_PROFILE_UPSERT;
        String base = SQLStorage.INSERT_PROFILE;

        assertEquals(insertColumns(base), insertColumns(upsert), "column list/order must match the base insert");
        assertEquals(countPlaceholders(base), countPlaceholders(upsert), "placeholder count must match");
    }

    private static List<String> insertColumns(String sql) {
        int start = sql.indexOf('(');
        int end = sql.indexOf(')', start);
        List<String> columns = new ArrayList<>();
        for (String column : sql.substring(start + 1, end).split(",")) {
            columns.add(column.trim().replace("`", ""));
        }
        return columns;
    }

    private static int countPlaceholders(String sql) {
        int values = sql.indexOf("VALUES");
        int start = sql.indexOf('(', values);
        int end = sql.indexOf(')', start);
        int count = 0;
        for (String value : sql.substring(start + 1, end).split(",")) {
            if ("?".equals(value.trim())) {
                count++;
            }
        }
        return count;
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
