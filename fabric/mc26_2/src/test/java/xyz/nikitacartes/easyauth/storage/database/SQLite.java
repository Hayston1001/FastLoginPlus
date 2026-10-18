/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package xyz.nikitacartes.easyauth.storage.database;

import java.sql.Connection;
import java.util.Map;

/** Test-only API fixture with EasyAuth's exact SQLite class name and reflected storage layout. */
public final class SQLite {
    private final Connection connection;
    private final StorageConfig config;

    public SQLite(Connection connection, String table) {
        this.connection = connection;
        config = new StorageConfig(table);
    }

    public Map<String, Object> getAllData() {
        throw new AssertionError("SQLite rename lookup must not load or deserialize the full account table");
    }

    public static final class StorageConfig {
        public final SQLiteConfig sqlite;

        private StorageConfig(String table) {
            sqlite = new SQLiteConfig(table);
        }
    }

    public static final class SQLiteConfig {
        public final String sqliteTable;

        private SQLiteConfig(String table) {
            sqliteTable = table;
        }
    }
}
