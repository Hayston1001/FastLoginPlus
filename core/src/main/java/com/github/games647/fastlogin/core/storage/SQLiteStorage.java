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

import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.zaxxer.hikari.HikariConfig;
import org.sqlite.JDBC;
import org.sqlite.SQLiteConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SQLiteStorage extends SQLStorage {

    protected static final String CREATE_TABLE_STMT = "CREATE TABLE IF NOT EXISTS `" + PREMIUM_TABLE + "` ("
            + "`UserID` INTEGER PRIMARY KEY AUTO_INCREMENT, "
            + "`UUID` CHAR(36), "
            // Minecraft usernames are case-insensitive: "Steve" and "steve" are the same account.
            // MySQL is case-insensitive by default (utf8mb4 collations), while SQLite's default
            // BINARY collation is case-sensitive. COLLATE NOCASE aligns SQLite with MySQL so the
            // same player cannot get two rows (premium + cracked) that differ only by letter case.
            + "`Name` VARCHAR(16) COLLATE NOCASE NOT NULL, "
            + "`Premium` BOOLEAN NOT NULL, "
            + "`LastIp` VARCHAR(255) NOT NULL, "
            + "`LastLogin` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            //the premium shouldn't steal the cracked account by changing the name
            + "`Floodgate` INTEGER(3), "
            + "UNIQUE (`Name`) "
            + ')';

    private static final String SQLITE_DRIVER = "org.sqlite.SQLiteDataSource";
    private final Lock lock = new ReentrantLock();

    public SQLiteStorage(PlatformPlugin<?> plugin, String databasePath, HikariConfig config) {
        super(plugin.getLog(), plugin.getName(), plugin.getThreadFactory(),
                setParams(config, replacePathVariables(plugin.getPluginFolder(), databasePath)));
    }

    private static HikariConfig setParams(HikariConfig config, String path) {
        config.setDataSourceClassName(SQLITE_DRIVER);

        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(1);

        config.addDataSourceProperty("url", JDBC.PREFIX + path);

        SQLiteConfig sqLiteConfig = new SQLiteConfig();

        // Use WAL (Write-Ahead Logging) mode to allow concurrent reads while a write is in progress.
        // This avoids blocking the entire database on single-writer operations, which is critical
        // under the proxy architecture where multiple async tasks may read/write from different threads.
        sqLiteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);

        // Set a busy timeout so that SQLite waits up to 5 seconds for the lock instead of
        // immediately throwing SQLITE_BUSY. This prevents "database is locked" errors when
        // two operations arrive back-to-back and one is still committing.
        sqLiteConfig.setBusyTimeout(5000);

        // a try to fix https://www.spigotmc.org/threads/fastlogin.101192/page-26#post-1874647
        // format strings retrieved by the timestamp column to match them from MySQL
        // vs the default: yyyy-MM-dd HH:mm:ss.SSS
        try {
            SQLiteConfig.class.getDeclaredMethod("setDateStringFormat", String.class);
            sqLiteConfig.setDateStringFormat("yyyy-MM-dd HH:mm:ss");
        } catch (NoSuchMethodException noSuchMethodException) {
            // Versions below this driver version do set the default timestamp value, so this change is not necessary
        }

        config.addDataSourceProperty("config", sqLiteConfig);

        return config;
    }

    @Override
    public StoredProfile loadProfile(String name) {
        lock.lock();
        try {
            return super.loadProfile(name);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public StoredProfile loadProfile(UUID uuid) {
        lock.lock();
        try {
            return super.loadProfile(uuid);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void save(StoredProfile playerProfile) {
        lock.lock();
        try {
            super.save(playerProfile);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean deleteProfile(String name) {
        lock.lock();
        try {
            return super.deleteProfile(name);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void createTables() throws SQLException {
        super.createTables();
        migrateCaseInsensitiveName();
    }

    /**
     * Older databases created before the COLLATE NOCASE column definition are case-sensitive on
     * Name lookups, which lets the same player (e.g. "Steve" vs "steve") end up with two rows.
     * SQLite cannot change a column collation in place, so the table is rebuilt: rename → recreate
     * with the new schema → copy data → drop the old table, all in one transaction.
     */
    private void migrateCaseInsensitiveName() throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT `sql` FROM `sqlite_master` "
                     + "WHERE `type`='table' AND `name`='" + PREMIUM_TABLE + "'")) {

            if (!rs.next()) {
                // no existing table: it was just created with the new schema
                return;
            }

            String createSql = rs.getString(1);
            if (createSql == null || !createSql.toUpperCase(Locale.ENGLISH).contains("COLLATE NOCASE")) {
                log.info("Migrating premium table to case-insensitive Name collation (COLLATE NOCASE)");
                stmt.execute("BEGIN");
                try {
                    // Best-effort cleanup in case a previous run died mid-migration outside the
                    // transaction. The transaction below normally rolls the rename back.
                    stmt.execute("DROP TABLE IF EXISTS `premium_old`");
                    stmt.execute("ALTER TABLE `" + PREMIUM_TABLE + "` RENAME TO `premium_old`");
                    stmt.execute(getCreateTableStmt());
                    // A case-sensitive database can hold several rows that differ only by case
                    // (exactly the premium + cracked split we now forbid). Copying them all would
                    // violate the new UNIQUE(Name) NOCASE constraint, so keep one row per name
                    // variant: premium rows win, otherwise the oldest (lowest UserID).
                    stmt.execute("INSERT INTO `" + PREMIUM_TABLE + "` "
                            + "(`UserID`, `UUID`, `Name`, `Premium`, `Floodgate`, `LastIp`, `LastLogin`) "
                            + "SELECT `UserID`, `UUID`, `Name`, `Premium`, `Floodgate`, `LastIp`, `LastLogin` "
                            + "FROM `premium_old` AS p "
                            + "WHERE `UserID` = ("
                            + "SELECT `UserID` FROM `premium_old` AS q "
                            + "WHERE lower(q.`Name`) = lower(p.`Name`) "
                            + "ORDER BY q.`Premium` DESC, q.`UserID` LIMIT 1)");
                    int totalRows;
                    try (ResultSet count = stmt.executeQuery("SELECT COUNT(*) FROM `premium_old`")) {
                        count.next();
                        totalRows = count.getInt(1);
                    }
                    int distinctNames;
                    try (ResultSet count = stmt.executeQuery(
                            "SELECT COUNT(*) FROM (SELECT 1 FROM `premium_old` GROUP BY lower(`Name`))")) {
                        count.next();
                        distinctNames = count.getInt(1);
                    }
                    int dropped = totalRows - distinctNames;
                    if (dropped > 0) {
                        log.warn("Dropped {} duplicate case-variant row(s) during migration; kept the "
                                + "premium row (or the oldest) per player. Affected players with a "
                                + "kept cracked row will log in as premium from now on.", dropped);
                    }
                    stmt.execute("DROP TABLE `premium_old`");
                    stmt.execute("COMMIT");
                    log.info("Migrated premium table to case-insensitive Name collation (COLLATE NOCASE)");
                } catch (SQLException ex) {
                    stmt.execute("ROLLBACK");
                    log.warn("Failed to migrate premium table to case-insensitive Name collation; "
                            + "rolled back, the original table is unchanged", ex);
                    throw ex;
                }
            }
        }
    }

    @Override
    protected String getCreateTableStmt() {
        // SQLite has a different syntax for auto increment
        return CREATE_TABLE_STMT.replace("AUTO_INCREMENT", "AUTOINCREMENT");
    }

    private static String replacePathVariables(Path dataFolder, String input) {
        String pluginFolder = dataFolder.toAbsolutePath().toString();
        return input.replace("{pluginDir}", pluginFolder);
    }
}
