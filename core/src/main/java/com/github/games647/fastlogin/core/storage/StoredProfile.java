/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.storage;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.fastlogin.core.shared.FloodgateState;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class StoredProfile extends Profile {

    private long rowId;
    private final ReentrantLock saveLock = new ReentrantLock();

    private boolean premium;
    private boolean sameNameCracked;
    private FloodgateState floodgate;
    private String lastIp;
    private Instant lastLogin;
    private String invalidUuid;
    private boolean uuidRepairRequired;

    public StoredProfile(long rowId, UUID uuid, String playerName, boolean premium, FloodgateState floodgate,
                         String lastIp, Instant lastLogin) {
        super(uuid, playerName);

        this.rowId = rowId;
        this.premium = premium;
        this.floodgate = floodgate;
        this.lastIp = lastIp;
        this.lastLogin = lastLogin;
    }

    public StoredProfile(UUID uuid, String playerName, boolean premium, FloodgateState isFloodgate, String lastIp) {
        this(-1, uuid, playerName, premium, isFloodgate, lastIp, Instant.now());
    }

    @Deprecated
    public StoredProfile(UUID uuid, String playerName, boolean premium, String lastIp) {
        this(-1, uuid, playerName, premium, FloodgateState.FALSE, lastIp, Instant.now());
    }

    public ReentrantLock getSaveLock() {
        return saveLock;
    }

    synchronized void retainInvalidUuid(String rawUuid) {
        invalidUuid = rawUuid;
        uuidRepairRequired = premium;
    }

    synchronized String getInvalidUuid() {
        return invalidUuid;
    }

    /**
     * A corrupt premium-owned row stays blocked even if a callback changes its UUID or consent.
     * SQL NULL is a supported absence and does not set this flag.
     *
     * @return whether the loaded row requires operator repair before login or saving
     */
    public synchronized boolean isUuidRepairRequired() {
        return uuidRepairRequired;
    }

    public synchronized boolean isExistingPlayer() {
        return rowId >= 0;
    }

    public synchronized void setPlayerName(String playerName) {
        this.name = playerName;
    }

    public synchronized long getRowId() {
        return rowId;
    }

    public synchronized void setRowId(long generatedId) {
        this.rowId = generatedId;
    }

    // can be null
    public synchronized UUID getId() {
        return id;
    }

    public synchronized Optional<UUID> getOptId() {
        return Optional.ofNullable(id);
    }

    public synchronized void setId(UUID uniqueId) {
        this.id = uniqueId;
    }

    /**
     * @return whether the online mode should be enabled for this player
     * @deprecated {@link #isOnlinemodePreferred()} is recommended, because the name represents more its
     * meaning
     */
    @Deprecated
    public synchronized boolean isPremium() {
        return premium;
    }

    /**
     * Return the recorded upgrade consent for this player.
     * <p>
     * <b>
     * Note: {@code false} doesn't represent that the player is offline. It could also mean that the player is premium,
     * but didn't activated the premium login mode yet. The flag is only set by an explicit consent (the premium
     * toggle) or a confirmed premium outcome — never by a login that has not been decided yet.
     * </b>
     *
     * @return whether the premium login is consented for this name
     */
    public synchronized boolean isOnlinemodePreferred() {
        return premium;
    }

    /**
     * @param premium whether online mode is preferred
     * @deprecated {@link #setOnlinemodePreferred(boolean)} is recommended, because of the better method name
     */
    @Deprecated
    public synchronized void setPremium(boolean premium) {
        this.premium = premium;
    }

    public synchronized void setOnlinemodePreferred(boolean premium) {
        this.premium = premium;
    }

    /**
     * The same-name cracked observation: a premium verification for this name was
     * observed to fail and no later verification passed. It records the observation
     * persistently per name, so every later connection can skip the doomed verification
     * attempt and go straight to the password flow.
     *
     * @return true when the name was observed as a same-name cracked player
     */
    public synchronized boolean isSameNameCracked() {
        return sameNameCracked;
    }

    /**
     * @param sameNameCracked true to record the failed premium verification observation
     */
    public synchronized void setSameNameCracked(boolean sameNameCracked) {
        this.sameNameCracked = sameNameCracked;
    }

    public synchronized FloodgateState getFloodgate() {
        return floodgate;
    }

    public synchronized boolean isFloodgateMigrated() {
        return floodgate != FloodgateState.NOT_MIGRATED;
    }

    public synchronized void setFloodgate(FloodgateState floodgate) {
        this.floodgate = floodgate;
    }

    public synchronized String getLastIp() {
        return lastIp;
    }

    public synchronized void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public synchronized Instant getLastLogin() {
        return lastLogin;
    }

    public synchronized void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof StoredProfile)) {
            return false;
        }

        StoredProfile that = (StoredProfile) o;
        if (!super.equals(o)) {
            return false;
        }

        return rowId == that.rowId && premium == that.premium && sameNameCracked == that.sameNameCracked
                && Objects.equals(lastIp, that.lastIp) && lastLogin.equals(that.lastLogin);
    }

    @Override
    public synchronized int hashCode() {
        return Objects.hash(super.hashCode(), rowId, premium, sameNameCracked, lastIp, lastLogin);
    }

    @Override
    public synchronized String toString() {
        return this.getClass().getSimpleName() + '{'
                + "rowId=" + rowId
                + ", premium=" + premium
                + ", sameNameCracked=" + sameNameCracked
                + ", floodgate=" + floodgate
                + ", lastIp='" + lastIp + '\''
                + ", lastLogin=" + lastLogin
                + "} " + super.toString();
    }
}
