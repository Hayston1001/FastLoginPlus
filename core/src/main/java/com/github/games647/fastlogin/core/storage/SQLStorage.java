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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static java.sql.Statement.RETURN_GENERATED_KEYS;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public abstract class SQLStorage implements AuthStorage {

    protected static final String PREMIUM_TABLE = "premium";
    protected static final String CREATE_TABLE_STMT = "CREATE TABLE IF NOT EXISTS `" + PREMIUM_TABLE + "` ("
            + "`UserID` INTEGER PRIMARY KEY AUTO_INCREMENT, "
            + "`UUID` CHAR(36), "
            + "`Name` VARCHAR(16) NOT NULL, "
            + "`Premium` BOOLEAN NOT NULL, "
            + "`LastIp` VARCHAR(255) NOT NULL, "
            + "`LastLogin` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            //the premium shouldn't steal the cracked account by changing the name
            + "UNIQUE (`Name`) "
            + ')';

    protected static final String ADD_FLOODGATE_COLUMN_STMT = "ALTER TABLE `" + PREMIUM_TABLE
            + "` ADD COLUMN `Floodgate` INTEGER(3)";

    protected static final String LOAD_BY_NAME = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `Name`=? LIMIT 1";
    protected static final String LOAD_BY_UUID = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `UUID`=? LIMIT 1";
    protected static final String INSERT_PROFILE = "INSERT INTO `" + PREMIUM_TABLE
            + "` (`UUID`, `Name`, `Premium`, `Floodgate`, `LastIp`) " + "VALUES (?, ?, ?, ?, ?) ";
    // 0.5.0/F020: fallback row-id lookup for the upsert update branch, where
    // getGeneratedKeys() behavior is driver-dependent and may return no row
    protected static final String SELECT_ID_BY_NAME = "SELECT `UserID` FROM `" + PREMIUM_TABLE
            + "` WHERE `Name`=?";
    // limit not necessary here, because it's unique
    protected static final String UPDATE_PROFILE = "UPDATE `" + PREMIUM_TABLE
            + "` SET `UUID`=?, `Name`=?, `Premium`=?, `Floodgate`=?, `LastIp`=?, "
            + "`LastLogin`=CURRENT_TIMESTAMP WHERE `UserID`=?";
    protected static final String DELETE_BY_NAME = "DELETE FROM `" + PREMIUM_TABLE
            + "` WHERE `Name`=?";

    // 0.5.0/F020: name-level striped locks close the cross-thread
    // load-modify-save window that the per-profile saveLock cannot cover (it is
    // per StoredProfile instance, so two threads holding different instances of
    // the same row silently overwrite each other).  Callers that perform a
    // load() followed by a save() of the same row must wrap the whole window in
    // withNameLock().  The global SQLite lock stays as-is (it is the degenerate
    // case of a single stripe); MySQL has no other guard at all.
    private static final int NAME_LOCK_STRIPES = 64;
    private final ReentrantLock[] nameLocks = new ReentrantLock[NAME_LOCK_STRIPES];

    // Web UI queries - pagination and search
    protected static final String LOAD_ALL_PAGED = "SELECT * FROM `" + PREMIUM_TABLE
            + "` ORDER BY `LastLogin` DESC LIMIT ? OFFSET ?";
    // No LIMIT/OFFSET: searchProfiles() merges an offline-UUID scan on top of this
    // result, so pagination is applied in memory after the merge
    protected static final String SEARCH_BY_NAME_OR_UUID = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE LOWER(`Name`) LIKE LOWER(?) OR LOWER(`UUID`) LIKE LOWER(?)"
            + " ORDER BY `LastLogin` DESC";
    protected static final String COUNT_ALL = "SELECT COUNT(*) FROM `" + PREMIUM_TABLE
            + "`";
    // Cracked players have no stored UUID; their panel UUID is computed from the name.
    // This scan feeds the offline-UUID search pass in searchProfiles().
    protected static final String SELECT_UUID_LESS = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `UUID` IS NULL";
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
     * name (0.5.0/F020).  All load-modify-save windows for the same name
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

    public void createTables() throws SQLException {
        // choose surrogate PK(ID), because UUID can be null for offline players
        // if UUID is always Premium UUID we would have to update offline player entries on insert
        // name cannot be PK, because it can be changed for premium players
        //todo: add unique uuid index usage
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate(getCreateTableStmt());

            // add Floodgate column
            DatabaseMetaData md = con.getMetaData();
            if (isColumnMissing(md, "Floodgate")) {
                stmt.executeUpdate(ADD_FLOODGATE_COLUMN_STMT);
            }

        }
    }

    private boolean isColumnMissing(DatabaseMetaData metaData, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, null, PREMIUM_TABLE, columnName)) {
            return !rs.next();
        }
    }

    /**
     * Look up a profile by name with strict "not found" semantics (0.6.0/F046).
     *
     * <p>{@link #loadProfile(String)} must keep returning a placeholder for
     * unknown names — the login flow relies on it. The WebUI (and any code
     * that must distinguish "unknown" from "known") uses this method
     * instead, so its 404 branches stay live and no rows are inserted for
     * unknown toggles.</p>
     *
     * @param name the player name to look up
     * @return the stored profile, or {@code null} when the name is unknown
     *         or the query failed
     */
    @Override
    public StoredProfile findProfileByName(String name) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement loadStmt = con.prepareStatement(LOAD_BY_NAME)
        ) {
            loadStmt.setString(1, name);

            try (ResultSet resultSet = loadStmt.executeQuery()) {
                return parseResult(resultSet).orElse(null);
            }
        } catch (SQLException sqlEx) {
            log.error("Failed to query profile: {}", name, sqlEx);
        }

        return null;
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
        }

        return null;
    }

    private Optional<StoredProfile> parseResult(ResultSet resultSet) throws SQLException {
        if (resultSet.next()) {
            return Optional.of(readCurrentRow(resultSet));
        }

        return Optional.empty();
    }

    /**
     * Read a StoredProfile from the current row without advancing the cursor.
     * Used by loadAllProfiles/searchProfiles where the caller already called rs.next().
     *
     * @param resultSet the ResultSet positioned at the current row
     * @return the StoredProfile read from the current row
     * @throws SQLException if a database access error occurs
     */
    private StoredProfile readCurrentRow(ResultSet resultSet) throws SQLException {
        long userId = resultSet.getInt("UserID");

        UUID uuid = Optional.ofNullable(resultSet.getString("UUID")).map(UUIDAdapter::parseId).orElse(null);

        String name = resultSet.getString("Name");
        boolean premium = resultSet.getBoolean("Premium");
        int floodgateNum = resultSet.getInt("Floodgate");
        FloodgateState floodgate;

        // if the player wasn't migrated to the new database format
        if (resultSet.wasNull()) {
            floodgate = FloodgateState.NOT_MIGRATED;
        } else {
            floodgate = FloodgateState.fromInt(floodgateNum);
        }

        String lastIp = resultSet.getString("LastIp");
        java.sql.Timestamp ts = resultSet.getTimestamp("LastLogin");
        Instant lastLogin = (ts != null) ? ts.toInstant() : java.time.Instant.EPOCH;
        return new StoredProfile(userId, uuid, name, premium, floodgate, lastIp, lastLogin);
    }

    @Override
    public void save(StoredProfile playerProfile) {
        saveQuietly(playerProfile);
    }

    /**
     * Save the profile, reporting SQL failures through the return value instead
     * of silently swallowing them (0.5.0/F020).  New profiles are saved with an
     * upsert so a concurrent first-time save of the same name can neither throw
     * on the UNIQUE(Name) constraint nor lose the profile.
     *
     * @param playerProfile profile to persist
     * @return true on success; false on a SQL error (already logged)
     */
    public boolean saveQuietly(StoredProfile playerProfile) {
        try (Connection con = dataSource.getConnection()) {
            String uuid = playerProfile.getOptId().map(UUIDAdapter::toMojangId).orElse(null);

            playerProfile.getSaveLock().lock();
            try {
                if (playerProfile.isExistingPlayer()) {
                    try (PreparedStatement saveStmt = con.prepareStatement(UPDATE_PROFILE)) {
                        saveStmt.setString(1, uuid);
                        saveStmt.setString(2, playerProfile.getName());
                        saveStmt.setBoolean(3, playerProfile.isOnlinemodePreferred());
                        saveStmt.setInt(4, playerProfile.getFloodgate().getValue());
                        saveStmt.setString(5, playerProfile.getLastIp());

                        saveStmt.setLong(6, playerProfile.getRowId());
                        saveStmt.execute();
                    }
                } else {
                    try (PreparedStatement saveStmt = con.prepareStatement(getInsertProfileStmt(),
                            RETURN_GENERATED_KEYS)) {
                        saveStmt.setString(1, uuid);

                        saveStmt.setString(2, playerProfile.getName());
                        saveStmt.setBoolean(3, playerProfile.isOnlinemodePreferred());
                        saveStmt.setInt(4, playerProfile.getFloodgate().getValue());
                        saveStmt.setString(5, playerProfile.getLastIp());

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
     * Fill in the row id of a freshly upserted profile (0.5.0/F020).
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
     * Load profiles with pagination.
     *
     * @param offset the offset (0-based)
     * @param limit  the maximum number of results
     * @return a list of stored profiles
     */
    public java.util.List<StoredProfile> loadAllProfiles(int offset, int limit) {
        java.util.List<StoredProfile> profiles = new java.util.ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement(LOAD_ALL_PAGED)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    profiles.add(readCurrentRow(rs));
                }
            }
        } catch (Exception ex) {
            log.error("Failed to load profiles", ex);
            // 0.6.0/F010: an empty list reads as "no players" downstream —
            // surface the outage instead of swallowing it
            throw new StorageUnavailableException("Failed to load profiles", ex);
        }
        return profiles;
    }

    /**
     * Combined search result: one page of matches plus the total match count.
     *
     * <p>Search and count come from a single pass because the result set is not
     * purely the SQL LIKE match — offline display UUIDs are computed (never stored),
     * so a supplementary in-memory scan can contribute rows. Counting them in a
     * separate query would desynchronize pagination.</p>
     */
    public static final class ProfileSearchResult {

        private final java.util.List<StoredProfile> profiles;
        private final int total;

        private ProfileSearchResult(java.util.List<StoredProfile> profiles, int total) {
            this.profiles = profiles;
            this.total = total;
        }

        /**
         * @return the profiles on the requested page
         */
        public java.util.List<StoredProfile> getProfiles() {
            return profiles;
        }

        /**
         * @return the total number of matching profiles across all pages
         */
        public int getTotal() {
            return total;
        }
    }

    /**
     * Search profiles by name or UUID (full or fragment) with pagination.
     *
     * <p>UUID matching is dash-insensitive: the database stores UUIDs in Mojang
     * trimmed form (no dashes — {@code UUIDAdapter.toMojangId}), while users paste
     * the dashed form. Cracked players additionally have no stored UUID at all —
     * the panel shows a computed offline UUID ({@code OfflinePlayer:<name>}, see
     * {@link StoredProfile#getDisplayUuid()}) — so when the query looks like a UUID
     * fragment, those computed UUIDs are matched in a supplementary in-memory scan
     * of the UUID-less rows.</p>
     *
     * @param query  the search query (substring match on Name, stored UUID or offline display UUID)
     * @param offset the offset (0-based)
     * @param limit  the maximum number of results per page
     * @return the matching page and the total match count
     */
    public ProfileSearchResult searchProfiles(String query, int offset, int limit) {
        String namePattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
        // Normalize to the stored (trimmed, lowercase) UUID form so dashed full UUIDs
        // and dashed fragments both match the `UUID` column
        String uuidQuery = query.replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
        String uuidPattern = "%" + uuidQuery + "%";

        // Name-keyed merge map: Name is UNIQUE, and the SQL pass and the offline-UUID
        // pass can both match the same row
        Map<String, StoredProfile> matches = new LinkedHashMap<>();

        // SQL pass over Name and the stored UUID column — no LIMIT/OFFSET here because
        // the offline-UUID pass below may contribute more rows; pagination is applied
        // after merging
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement(SEARCH_BY_NAME_OR_UUID)) {
            stmt.setString(1, namePattern);
            stmt.setString(2, uuidPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StoredProfile profile = readCurrentRow(rs);
                    matches.put(profile.getName().toLowerCase(Locale.ROOT), profile);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to search profiles", ex);
            // 0.6.0/F010: surface the outage instead of swallowing it
            throw new StorageUnavailableException("Failed to search profiles", ex);
        }

        if (isUuidFragment(uuidQuery)) {
            matches.putAll(findByOfflineUuid(uuidQuery));
        }

        java.util.List<StoredProfile> all = new ArrayList<>(matches.values());
        // Mirror the SQL ORDER BY LastLogin DESC for the merged result
        all.sort(Comparator.comparing(StoredProfile::getLastLogin,
                Comparator.nullsFirst(Comparator.naturalOrder())).reversed());

        int total = all.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        return new ProfileSearchResult(all.subList(from, to), total);
    }

    /**
     * Check whether a normalized query looks like a UUID fragment (hex digits only).
     *
     * @param normalizedQuery dash/space-stripped, lowercased query
     * @return true if it is long enough to be meaningful and contains only hex digits
     */
    private static boolean isUuidFragment(String normalizedQuery) {
        return normalizedQuery.length() >= 4 && normalizedQuery.matches("[0-9a-f]+");
    }

    /**
     * Match the query against computed offline UUIDs of players without a stored UUID.
     *
     * <p>Cracked (and not-yet-migrated Floodgate) players have {@code UUID = NULL};
     * the panel displays a deterministic offline UUID computed from the name, which
     * exists nowhere in the database — a plain LIKE search can therefore never find
     * them by UUID.</p>
     *
     * @param uuidQuery dash-stripped, lowercased UUID query
     * @return matching profiles keyed by lowercase name
     */
    private Map<String, StoredProfile> findByOfflineUuid(String uuidQuery) {
        Map<String, StoredProfile> found = new LinkedHashMap<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement(SELECT_UUID_LESS);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                StoredProfile profile = readCurrentRow(rs);
                String displayUuid = profile.getDisplayUuid().replace("-", "");
                if (displayUuid.contains(uuidQuery)) {
                    found.put(profile.getName().toLowerCase(Locale.ROOT), profile);
                }
            }
        } catch (Exception ex) {
            // Best-effort supplement to the SQL pass — a failure here must not lose
            // the rows the SQL pass already found
            log.error("Failed to scan offline UUIDs", ex);
        }
        return found;
    }

    /**
     * Count total profiles or profiles matching a search query.
     *
     * <p>With a query this delegates to {@link #searchProfiles(String, int, int)} so
     * the count includes exactly the same matches (including offline display UUIDs)
     * as the list endpoint.</p>
     *
     * @param query the search query, or null to count all
     * @return the count
     */
    public int countProfiles(String query) {
        if (query != null && !query.isEmpty()) {
            return searchProfiles(query, 0, 0).getTotal();
        }

        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement(COUNT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            log.error("Failed to count profiles", ex);
            // 0.6.0/F010: surface the outage instead of reporting 0 players
            throw new StorageUnavailableException("Failed to count profiles", ex);
        }
        return 0;
    }

    /**
     * Get the database type (e.g., "sqlite", "mysql").
     *
     * @return the database type string
     */
    public String getDatabaseType() {
        String jdbcUrl = dataSource.getJdbcUrl();
        if (jdbcUrl == null) {
            return "Unknown";
        }
        return jdbcUrl.contains("sqlite") ? "SQLite" : "MySQL";
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
     * second profile (0.5.0/F020).
     *
     * @return an insert statement with five parameters
     *         (UUID, Name, Premium, Floodgate, LastIp)
     */
    protected String getInsertProfileStmt() {
        return INSERT_PROFILE;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
