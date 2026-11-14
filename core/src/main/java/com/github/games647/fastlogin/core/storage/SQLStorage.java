/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static java.sql.Statement.RETURN_GENERATED_KEYS;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public abstract class SQLStorage implements AuthStorage {

    protected static final String PREMIUM_TABLE = "premium";
    // Bedrock/Floodgate prefixed names can exceed the 16 characters of a Java
    // username; the declared width only documents the column, it is not enforced
    protected static final int NAME_COLUMN_LENGTH = 64;

    protected static final String UUID_INDEX_NAME = "idx_premium_uuid";
    protected static final String CREATE_UUID_INDEX_STMT = "CREATE INDEX `" + UUID_INDEX_NAME + "` ON `"
            + PREMIUM_TABLE + "` (`UUID`)";
    // only MySQL/MariaDB enforce the declared width
    protected static final String WIDEN_NAME_COLUMN_STMT = "ALTER TABLE `" + PREMIUM_TABLE
            + "` MODIFY COLUMN `Name` VARCHAR(" + NAME_COLUMN_LENGTH + ") NOT NULL";

    // MySQL/MariaDB ER_DUP_KEYNAME: a concurrent start created the index first
    private static final int DUPLICATE_KEY_NAME_ERROR = 1061;

    protected static final String CREATE_TABLE_STMT = "CREATE TABLE IF NOT EXISTS `" + PREMIUM_TABLE + "` ("
            + "`UserID` INTEGER PRIMARY KEY AUTO_INCREMENT, "
            + "`UUID` CHAR(36), "
            + "`Name` VARCHAR(" + NAME_COLUMN_LENGTH + ") NOT NULL, "
            + "`Premium` BOOLEAN NOT NULL, "
            + "`LastIp` VARCHAR(255) NOT NULL, "
            + "`LastLogin` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            //the premium shouldn't steal the cracked account by changing the name
            + "UNIQUE (`Name`) "
            + ')';

    protected static final String ADD_FLOODGATE_COLUMN_STMT = "ALTER TABLE `" + PREMIUM_TABLE
            + "` ADD COLUMN `Floodgate` INTEGER(3)";

    // the persistent same-name cracked observation; rows that predate the column
    // read as "not observed" (NULL -> false)
    protected static final String ADD_SAME_NAME_CRACKED_COLUMN_STMT = "ALTER TABLE `" + PREMIUM_TABLE
            + "` ADD COLUMN `SameNameCracked` INTEGER(3)";

    protected static final String LOAD_BY_NAME = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `Name`=? LIMIT 1";
    protected static final String LOAD_BY_UUID = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `UUID`=? LIMIT 1";
    protected static final String LOAD_BY_ROW_ID = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `UserID`=? LIMIT 1";
    protected static final String INSERT_PROFILE = "INSERT INTO `" + PREMIUM_TABLE
            + "` (`UUID`, `Name`, `Premium`, `Floodgate`, `SameNameCracked`, `LastIp`) "
            + "VALUES (?, ?, ?, ?, ?, ?) ";
    // fallback row-id lookup for the upsert update branch, where
    // getGeneratedKeys() behavior is driver-dependent and may return no row
    protected static final String SELECT_ID_BY_NAME = "SELECT `UserID` FROM `" + PREMIUM_TABLE
            + "` WHERE `Name`=?";
    // limit not necessary here, because it's unique
    protected static final String UPDATE_PROFILE = "UPDATE `" + PREMIUM_TABLE
            + "` SET `UUID`=?, `Name`=?, `Premium`=?, `Floodgate`=?, `SameNameCracked`=?, `LastIp`=?, "
            + "`LastLogin`=CURRENT_TIMESTAMP WHERE `UserID`=?";
    protected static final String DELETE_BY_NAME = "DELETE FROM `" + PREMIUM_TABLE
            + "` WHERE `Name`=?";
    // observation bookkeeping: clears only the SameNameCracked
    // column, never any other field or table
    protected static final String CLEAR_SAME_NAME_CRACKED_STMT = "UPDATE `" + PREMIUM_TABLE
            + "` SET `SameNameCracked`=0 WHERE `SameNameCracked` != 0";

    // name-level striped locks close the cross-thread
    // load-modify-save window that the per-profile saveLock cannot cover (it is
    // per StoredProfile instance, so two threads holding different instances of
    // the same row silently overwrite each other).  Callers that perform a
    // load() followed by a save() of the same row must wrap the whole window in
    // withNameLock().  The global SQLite lock stays as-is (it is the degenerate
    // case of a single stripe); MySQL has no other guard at all.
    private static final int NAME_LOCK_STRIPES = 64;
    private final ReentrantLock[] nameLocks = new ReentrantLock[NAME_LOCK_STRIPES];

    protected final Logger log;
    protected final HikariDataSource dataSource;

    public SQLStorage(Logger log, String poolName, ThreadFactory threadFactory, HikariConfig config) {
        this.log = log;
        config.setPoolName(poolName);
        if (threadFactory != null) {
            config.setThreadFactory(threadFactory);
        }

        this.dataSource = new HikariDataSource(config);

        for (int i = 0; i < nameLocks.length; i++) {
            nameLocks[i] = new ReentrantLock();
        }
    }

    /**
     * Run the action while holding the striped lock bucket of the given player
     * name.  All load-modify-save windows for the same name
     * serialize on this lock, so no lost update can occur between concurrent
     * flows (login vs. admin command vs. plugin message task).
     *
     * @param name the player name the window operates on
     * @param action the load-modify-save window to run exclusively
     * @param <T> the action result type
     * @return the action result
     */
    public <T> T withNameLock(String name, Supplier<T> action) {
        ReentrantLock lock = nameLocks[lockIndexFor(name)];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Runnable variant of {@link #withNameLock(String, Supplier)}.
     *
     * @param name the player name the window operates on
     * @param action the load-modify-save window to run exclusively
     */
    public void withNameLock(String name, Runnable action) {
        withNameLock(name, () -> {
            action.run();
            return null;
        });
    }

    private static int lockIndexFor(String name) {
        // & strip mask keeps the index in range for negative hash codes too
        return name == null ? 0 : name.hashCode() & (NAME_LOCK_STRIPES - 1);
    }

    /**
     * Persists the fields a login owns onto the current row while holding the
     * name-level lock. The row is re-read inside the window, so a snapshot
     * loaded before the login was assembled cannot overwrite a concurrent
     * toggle, and a record deleted in the meantime is not resurrected.
     *
     * @param name           the player name the window operates on
     * @param sessionProfile the profile this login assembled before the lock
     * @param changes        the fields this login owns, applied to the persisted row
     * @return the persisted row, or null when nothing was written
     */
    public StoredProfile saveProfileLocked(String name, StoredProfile sessionProfile,
                                           Consumer<StoredProfile> changes) {
        return withNameLock(name, () -> {
            StoredProfile current = loadProfile(name);
            if (current == null) {
                // the database could not be queried: fail closed instead of
                // writing the stale snapshot
                return null;
            }
            if (!current.isExistingPlayer()) {
                if (sessionProfile.isExistingPlayer()) {
                    // No row holds this name. That is either a record deleted
                    // while the login was in flight, or a premium rename: the
                    // row keeps its id and only changes its name, so the login's
                    // own row is still there under the previous name. Follow the
                    // rename instead of dropping the write (which left the name
                    // stale and made a later toggle insert a second row).
                    StoredProfile renamed = loadProfileByRowIdQuietly(sessionProfile.getRowId());
                    if (renamed == null) {
                        // the row was deleted while this login was in flight
                        return null;
                    }
                    renamed.setPlayerName(name);
                    changes.accept(renamed);
                    return saveQuietly(renamed) ? renamed : null;
                }
                changes.accept(sessionProfile);
                return saveQuietly(sessionProfile) ? sessionProfile : null;
            }
            changes.accept(current);
            return saveQuietly(current) ? current : null;
        });
    }

    public void createTables() throws SQLException {
        // choose surrogate PK(ID), because UUID can be null for offline players
        // if UUID is always Premium UUID we would have to update offline player entries on insert
        // name cannot be PK, because it can be changed for premium players
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate(getCreateTableStmt());

            // add Floodgate column
            DatabaseMetaData md = con.getMetaData();
            if (isColumnMissing(con, md, "Floodgate")) {
                stmt.executeUpdate(ADD_FLOODGATE_COLUMN_STMT);
            }

            // add the same-name cracked observation column
            if (isColumnMissing(con, md, "SameNameCracked")) {
                stmt.executeUpdate(ADD_SAME_NAME_CRACKED_COLUMN_STMT);
            }

        }

        migrateSchema();
    }

    /**
     * Applies the idempotent migrations that older tables need: the UUID
     * lookup index and the widened Name column. Runs after table creation and
     * on every startup, so existing databases are upgraded in place.
     *
     * @throws SQLException when a migration could not be applied
     */
    protected void migrateSchema() throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            DatabaseMetaData md = con.getMetaData();
            widenNameColumn(con, stmt, md);
            createUuidIndex(con, stmt, md);
        }
    }

    private void widenNameColumn(Connection con, Statement stmt, DatabaseMetaData md) throws SQLException {
        String widenStmt = getWidenNameColumnStmt();
        if (widenStmt == null) {
            return;
        }

        int columnSize = getColumnSize(con, md, "Name");
        if (columnSize > 0 && columnSize < NAME_COLUMN_LENGTH) {
            stmt.executeUpdate(widenStmt);
            log.info("Widened the premium Name column to VARCHAR({})", NAME_COLUMN_LENGTH);
        }
    }

    private void createUuidIndex(Connection con, Statement stmt, DatabaseMetaData md) throws SQLException {
        if (!isIndexMissing(con, md, UUID_INDEX_NAME)) {
            return;
        }

        try {
            stmt.executeUpdate(getCreateUuidIndexStmt());
            log.info("Created the premium UUID index {}", UUID_INDEX_NAME);
        } catch (SQLException ex) {
            if (ex.getErrorCode() != DUPLICATE_KEY_NAME_ERROR) {
                throw ex;
            }
            // another instance created the index between the check and the CREATE
            log.debug("The premium UUID index {} already exists", UUID_INDEX_NAME);
        }
    }

    private boolean isColumnMissing(Connection con, DatabaseMetaData metaData, String columnName)
            throws SQLException {
        try (ResultSet rs = metaData.getColumns(con.getCatalog(), null, PREMIUM_TABLE, columnName)) {
            return !rs.next();
        }
    }

    private int getColumnSize(Connection con, DatabaseMetaData metaData, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(con.getCatalog(), null, PREMIUM_TABLE, columnName)) {
            return rs.next() ? rs.getInt("COLUMN_SIZE") : -1;
        }
    }

    private boolean isIndexMissing(Connection con, DatabaseMetaData metaData, String indexName)
            throws SQLException {
        try (ResultSet rs = metaData.getIndexInfo(con.getCatalog(), null, PREMIUM_TABLE, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(name)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public StoredProfile loadProfile(String name) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement loadStmt = con.prepareStatement(LOAD_BY_NAME)
        ) {
            loadStmt.setString(1, name);

            try (ResultSet resultSet = loadStmt.executeQuery()) {
                return parseResult(resultSet).orElseGet(() -> new StoredProfile(null, name, false,
                        FloodgateState.FALSE, ""));
            }
        } catch (SQLException sqlEx) {
            log.error("Failed to query profile: {}", name, sqlEx);
        }

        return null;
    }

    /**
     * Loads the profile owning the given UUID.
     *
     * @param uuid the UUID to look up
     * @return the matching profile, or null when no row holds this UUID
     * @throws ProfileLookupException when the database could not be queried
     */
    @Override
    public StoredProfile loadProfile(UUID uuid) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement loadStmt = con.prepareStatement(LOAD_BY_UUID)) {
            loadStmt.setString(1, UUIDAdapter.toMojangId(uuid));

            try (ResultSet resultSet = loadStmt.executeQuery()) {
                return parseResult(resultSet).orElse(null);
            }
        } catch (SQLException sqlEx) {
            log.error("Failed to query profile: {}", uuid, sqlEx);
            throw new ProfileLookupException("Failed to query the profile for " + uuid, sqlEx);
        }
    }

    /**
     * Loads the row with the given id, treating an unreadable database as
     * "cannot tell": the caller writes nothing rather than guessing.
     *
     * @param rowId the surrogate primary key of the row
     * @return the row, or null when it no longer exists or could not be read
     */
    private StoredProfile loadProfileByRowIdQuietly(long rowId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement loadStmt = con.prepareStatement(LOAD_BY_ROW_ID)) {
            loadStmt.setLong(1, rowId);

            try (ResultSet resultSet = loadStmt.executeQuery()) {
                return parseResult(resultSet).orElse(null);
            }
        } catch (SQLException sqlEx) {
            log.error("Failed to query the profile row {}", rowId, sqlEx);
            return null;
        }
    }

    private Optional<StoredProfile> parseResult(ResultSet resultSet) throws SQLException {
        if (resultSet.next()) {
            long userId = resultSet.getInt("UserID");

            String name = resultSet.getString("Name");
            String storedUuid = resultSet.getString("UUID");
            UUID uuid = null;
            boolean invalid = false;
            if (storedUuid != null) {
                try {
                    uuid = UUIDAdapter.parseId(storedUuid);
                } catch (IllegalArgumentException invalidUuid) {
                    invalid = true;
                }
            }

            boolean premium = resultSet.getBoolean("Premium");
            // NULL (rows older than the column) reads as "not observed"
            boolean sameNameCracked = resultSet.getBoolean("SameNameCracked");
            int floodgateNum = resultSet.getInt("Floodgate");
            FloodgateState floodgate;

            // if the player wasn't migrated to the new database format
            if (resultSet.wasNull()) {
                floodgate = FloodgateState.NOT_MIGRATED;
            } else {
                floodgate = FloodgateState.fromInt(floodgateNum);
            }

            String lastIp = resultSet.getString("LastIp");
            Instant lastLogin = resultSet.getTimestamp("LastLogin").toInstant();
            StoredProfile profile = new StoredProfile(userId, uuid, name, premium, floodgate, lastIp, lastLogin);
            if (invalid) {
                profile.retainInvalidUuid(storedUuid);
                log.warn("Invalid UUID column for player {} (UserID {}): using no UUID; {}", name, userId,
                        premium ? "premium login and profile saves blocked until operator repair"
                                : "raw UUID preserved on password-login saves");
            }
            profile.setSameNameCracked(sameNameCracked);
            return Optional.of(profile);
        }

        return Optional.empty();
    }

    @Override
    public void save(StoredProfile playerProfile) {
        saveQuietly(playerProfile);
    }

    /**
     * Save the profile, reporting SQL failures through the return value instead
     * of silently swallowing them.  New profiles are saved with an
     * upsert so a concurrent first-time save of the same name can neither throw
     * on the UNIQUE(Name) constraint nor lose the profile.
     *
     * @param playerProfile profile to persist
     * @return true on success; false on a SQL error or a corrupt premium UUID (already logged)
     */
    public boolean saveQuietly(StoredProfile playerProfile) {
        if (playerProfile.isUuidRepairRequired()) {
            log.warn("Refusing to save corrupt premium UUID row for {} (UserID {}): operator repair required",
                    playerProfile.getName(), playerProfile.getRowId());
            return false;
        }
        try (Connection con = dataSource.getConnection()) {
            String uuid = playerProfile.getOptId().map(UUIDAdapter::toMojangId)
                    .orElse(playerProfile.getInvalidUuid());

            playerProfile.getSaveLock().lock();
            try {
                if (playerProfile.isExistingPlayer()) {
                    try (PreparedStatement saveStmt = con.prepareStatement(UPDATE_PROFILE)) {
                        saveStmt.setString(1, uuid);
                        saveStmt.setString(2, playerProfile.getName());
                        saveStmt.setBoolean(3, playerProfile.isOnlinemodePreferred());
                        saveStmt.setInt(4, playerProfile.getFloodgate().getValue());
                        saveStmt.setBoolean(5, playerProfile.isSameNameCracked());
                        saveStmt.setString(6, playerProfile.getLastIp());

                        saveStmt.setLong(7, playerProfile.getRowId());
                        saveStmt.execute();
                    }
                } else {
                    try (PreparedStatement saveStmt = con.prepareStatement(getInsertProfileStmt(),
                            RETURN_GENERATED_KEYS)) {
                        saveStmt.setString(1, uuid);

                        saveStmt.setString(2, playerProfile.getName());
                        saveStmt.setBoolean(3, playerProfile.isOnlinemodePreferred());
                        saveStmt.setInt(4, playerProfile.getFloodgate().getValue());
                        saveStmt.setBoolean(5, playerProfile.isSameNameCracked());
                        saveStmt.setString(6, playerProfile.getLastIp());

                        saveStmt.execute();
                        backfillRowId(playerProfile, con, saveStmt);
                    }
                }
            } finally {
                playerProfile.getSaveLock().unlock();
            }
            return true;
        } catch (SQLException ex) {
            log.error("Failed to save playerProfile {}", playerProfile, ex);
            return false;
        }
    }

    /**
     * Fill in the row id of a freshly upserted profile.
     *
     * <p>When the upsert takes the update branch (the name already exists) the
     * behavior of {@code getGeneratedKeys()} is driver-dependent and may yield no
     * row, so fall back to a SELECT on the unique name column.  A stable row id
     * keeps the {@code isExistingPlayer()} semantics intact for later UPDATE
     * saves.</p>
     *
     * @param playerProfile the profile that was just upserted
     * @param con the connection the upsert ran on
     * @param upsertStmt the executed upsert statement
     * @throws SQLException on database errors
     */
    private void backfillRowId(StoredProfile playerProfile, Connection con, PreparedStatement upsertStmt)
            throws SQLException {
        try (ResultSet generatedKeys = upsertStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                playerProfile.setRowId(generatedKeys.getInt(1));
                return;
            }
        }

        try (PreparedStatement selectStmt = con.prepareStatement(SELECT_ID_BY_NAME)) {
            selectStmt.setString(1, playerProfile.getName());
            try (ResultSet resultSet = selectStmt.executeQuery()) {
                if (resultSet.next()) {
                    playerProfile.setRowId(resultSet.getInt(1));
                }
            }
        }
    }

    @Override
    public boolean deleteProfile(String name) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement deleteStmt = con.prepareStatement(DELETE_BY_NAME)) {
            deleteStmt.setString(1, name);
            return deleteStmt.executeUpdate() > 0;
        } catch (SQLException ex) {
            log.error("Failed to delete profile: {}", name, ex);
        }
        return false;
    }

    /**
     * Clears every same-name cracked observation in the database.
     *
     * <p>Only the {@code SameNameCracked} column is touched — no other field,
     * row property or table. Rows without a mark (including ones older than the
     * column, which read as "not observed") are left alone.</p>
     *
     * @return the number of cleared rows; -1 when the database could not be reached
     */
    public int clearSameNameCrackedObservations() {
        try (Connection con = dataSource.getConnection();
             PreparedStatement clearStmt = con.prepareStatement(CLEAR_SAME_NAME_CRACKED_STMT)) {
            return clearStmt.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to clear the same-name cracked observations", ex);
            return -1;
        }
    }

    /**
     * SQLite has a slightly different syntax, so this will be overridden by SQLiteStorage
     * @return An SQL Statement to create the `premium` table
     */
    protected String getCreateTableStmt() {
        return CREATE_TABLE_STMT;
    }

    /**
     * Insert statement for a new profile.  Storage implementations override
     * this with an upsert so that two concurrent first-time saves for the same
     * name cannot race the UNIQUE(Name) constraint and silently lose the
     * second profile.
     *
     * @return an insert statement with six parameters
     *         (UUID, Name, Premium, Floodgate, SameNameCracked, LastIp)
     */
    protected String getInsertProfileStmt() {
        return INSERT_PROFILE;
    }

    /**
     * Index statement for UUID lookups. Implementations whose SQL dialect
     * supports {@code IF NOT EXISTS} override this.
     *
     * @return the CREATE INDEX statement for the UUID column
     */
    protected String getCreateUuidIndexStmt() {
        return CREATE_UUID_INDEX_STMT;
    }

    /**
     * Statement widening the Name column for dialects that enforce the
     * declared width. SQLite does not, so it overrides this with null.
     *
     * @return the ALTER statement, or null when the dialect needs no widening
     */
    protected String getWidenNameColumnStmt() {
        return WIDEN_NAME_COLUMN_STMT;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
