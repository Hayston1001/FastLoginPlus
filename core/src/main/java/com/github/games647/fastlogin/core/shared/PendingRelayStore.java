/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable queue for proxy relay messages that could not be sent because no
 * player was online to serve as the plugin-message carrier.
 * <p>
 * The in-memory collections are the runtime source of truth; every mutation is
 * persisted to a small JSON file in the plugin folder so a backend restart does
 * not lose queued toggles/deletes. The file is rewritten atomically (temp file
 * + rename) on every change.
 * <p>
 * Toggle entries: key = player name, value = true for premium, false for cracked.
 * Delete entries: player names.
 * Premium notice entries: key = player name, value = true for AuthMe's
 * {@code premium.set}, false for {@code premium.unset}. These travel on AuthMe's own
 * channel ({@code authme:main}) rather than on FLP's, and they update a different cache
 * on the proxy, so they get their own slot instead of riding the toggle entries.
 * <p>
 * Every entry carries the time it was queued. Entries older than
 * {@link #MAX_ENTRY_AGE_MILLIS} are stale admin intent and are dropped on load and
 * on read; files written before timestamps existed inherit the load time.
 */
public class PendingRelayStore {

    private static final String FILE_NAME = "pending-relay.json";

    /** Entries older than this are dropped instead of being relayed days later. */
    public static final long MAX_ENTRY_AGE_MILLIS = 24L * 60L * 60L * 1000L;
    /**
     * Only a premium toggle this recent may relax the configure-phase connection
     * UUID check, so a forgotten entry cannot keep that exception alive.
     */
    public static final long PENDING_PREMIUM_RELAXATION_MILLIS = 5L * 60L * 1000L;

    private final Path file;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Boolean> toggles = new ConcurrentHashMap<>();
    private final Map<String, Long> toggleTimes = new ConcurrentHashMap<>();
    private final Set<String> deletes = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> deleteTimes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> premiumNotices = new ConcurrentHashMap<>();
    private final Map<String, Long> premiumNoticeTimes = new ConcurrentHashMap<>();

    /**
     * @param pluginFolder plugin data folder (the file lives directly in it)
     * @param logger logger for load/persist failures
     */
    public PendingRelayStore(Path pluginFolder, Logger logger) {
        this.file = pluginFolder.resolve(FILE_NAME);
        this.logger = logger;
    }

    /**
     * Loads the persisted queue from disk into memory (replacing any current content).
     * A corrupt file is moved aside as {@code pending-relay.json.corrupt} so it does
     * not fail every startup.
     *
     * @return true if any entry was restored
     */
    public synchronized boolean load() {
        if (Files.notExists(file)) {
            return false;
        }

        String json;
        try {
            json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.warn("Failed to read {}: {}", file, ex.getMessage());
            return false;
        }

        Data data;
        try {
            data = gson.fromJson(json, Data.class);
        } catch (Exception ex) {
            logger.warn("Failed to parse {} — moving it aside", file, ex);
            try {
                Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt");
                Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveEx) {
                logger.warn("Failed to move corrupt {} aside", file, moveEx);
            }
            return false;
        }

        if (data == null) {
            return false;
        }
        // the contract says "replace" — clear first so a second
        // load() cannot resurrect entries that were relayed and removed since
        toggles.clear();
        toggleTimes.clear();
        deletes.clear();
        deleteTimes.clear();
        premiumNotices.clear();
        premiumNoticeTimes.clear();
        long now = System.currentTimeMillis();
        boolean migrated = false;
        if (data.toggles != null) {
            toggles.putAll(data.toggles);
        }
        if (data.toggleTimes != null) {
            toggleTimes.putAll(data.toggleTimes);
            migrated = true;
        }
        if (data.deletes != null) {
            deletes.addAll(data.deletes);
        }
        if (data.deleteTimes != null) {
            deleteTimes.putAll(data.deleteTimes);
            migrated = true;
        }
        if (data.premiumNotices != null) {
            premiumNotices.putAll(data.premiumNotices);
        }
        if (data.premiumNoticeTimes != null) {
            premiumNoticeTimes.putAll(data.premiumNoticeTimes);
            migrated = true;
        }
        // a file written before timestamps existed is not stale: it starts aging now
        for (String name : toggles.keySet()) {
            migrated |= toggleTimes.putIfAbsent(name, now) == null;
        }
        for (String name : deletes) {
            migrated |= deleteTimes.putIfAbsent(name, now) == null;
        }
        for (String name : premiumNotices.keySet()) {
            migrated |= premiumNoticeTimes.putIfAbsent(name, now) == null;
        }
        boolean pruned = pruneExpired(now);
        if (migrated || pruned) {
            // rewrite once with timestamps and without the expired entries
            persist();
        }
        return !toggles.isEmpty() || !deletes.isEmpty() || !premiumNotices.isEmpty();
    }

    /**
     * Queues a premium/cracked toggle for later relay and persists it.
     * <p>
     * The retry task reads the queued value at send time (see {@link #removeToggle}),
     * so overwriting an existing entry with a new value does not require another
     * retry task.
     *
     * @param name player name
     * @param activate true for premium, false for cracked
     * @return true if this call created a new entry; false if an entry for the
     * name already existed — in that case a retry task is already running for it
     */
    public synchronized boolean queueToggle(String name, boolean activate) {
        Boolean previous = toggles.put(name, activate);
        if (previous == null || previous.booleanValue() != activate) {
            toggleTimes.put(name, System.currentTimeMillis());
            persist();
        }
        return previous == null;
    }

    /**
     * Removes a queued toggle (if present) and persists the removal.
     *
     * @param name player name
     * @return true if the entry existed and was removed
     */
    public synchronized boolean clearToggle(String name) {
        boolean removed = toggles.remove(name) != null;
        toggleTimes.remove(name);
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * Atomically removes a queued toggle and returns the value it carried.
     * <p>
     * Callers must send the returned value instead of a value captured when the
     * retry task was scheduled — the entry may have been overwritten by a newer
     * toggle command in the meantime, and the latest queued value is the one the
     * admin intended.
     *
     * @param name player name
     * @return the removed toggle value (true = premium, false = cracked), or
     * null if nothing was queued for the name
     */
    public synchronized Boolean removeToggle(String name) {
        Boolean value = toggles.remove(name);
        toggleTimes.remove(name);
        if (value != null) {
            persist();
        }
        return value;
    }

    /**
     * Queues a delete for later relay and persists it.
     *
     * @param name player name
     * @return true if this call created a new entry; false if the name was
     * already queued — in that case a retry task is already running for it
     */
    public synchronized boolean queueDelete(String name) {
        boolean added = deletes.add(name);
        if (added) {
            deleteTimes.put(name, System.currentTimeMillis());
            persist();
        }
        return added;
    }

    /**
     * Removes a queued delete (if present) and persists the removal.
     *
     * @param name player name
     * @return true if the entry existed and was removed
     */
    public synchronized boolean clearDelete(String name) {
        boolean removed = deletes.remove(name);
        deleteTimes.remove(name);
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * Queues an AuthMe premium notice for later relay and persists it.
     * <p>
     * The retry task reads the queued value at send time (see
     * {@link #removePremiumNotice}), so overwriting an existing entry with a newer state
     * does not require another retry task.
     *
     * @param name player name
     * @param isSet true for {@code premium.set}, false for {@code premium.unset}
     * @return true if this call created a new entry; false if an entry for the
     * name already existed — in that case a retry task is already running for it
     */
    public synchronized boolean queuePremiumNotice(String name, boolean isSet) {
        Boolean previous = premiumNotices.put(name, isSet);
        if (previous == null || previous.booleanValue() != isSet) {
            premiumNoticeTimes.put(name, System.currentTimeMillis());
            persist();
        }
        return previous == null;
    }

    /**
     * Removes a queued premium notice (if present) and persists the removal.
     *
     * @param name player name
     * @return true if the entry existed and was removed
     */
    public synchronized boolean clearPremiumNotice(String name) {
        boolean removed = premiumNotices.remove(name) != null;
        premiumNoticeTimes.remove(name);
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * Atomically removes a queued premium notice and returns the value it carried.
     *
     * @param name player name
     * @return the removed notice value (true = set, false = unset), or null if
     * nothing was queued for the name
     */
    public synchronized Boolean removePremiumNotice(String name) {
        Boolean value = premiumNotices.remove(name);
        premiumNoticeTimes.remove(name);
        if (value != null) {
            persist();
        }
        return value;
    }

    /**
     * @param name player name
     * @return the queued notice value, or null if not queued
     */
    public synchronized Boolean getPremiumNotice(String name) {
        pruneExpired(System.currentTimeMillis());
        return premiumNotices.get(name);
    }

    /**
     * @param name player name
     * @return true if a premium notice is queued for the name
     */
    public synchronized boolean containsPremiumNotice(String name) {
        pruneExpired(System.currentTimeMillis());
        return premiumNotices.containsKey(name);
    }

    /**
     * @return snapshot of the queued premium notices (copy — safe to iterate)
     */
    public synchronized Map<String, Boolean> premiumNotices() {
        pruneExpired(System.currentTimeMillis());
        return new LinkedHashMap<>(premiumNotices);
    }

    /**
     * Clears all entries and persists the empty queue (used when the proxy
     * support is disabled and queued work can no longer be delivered).
     */
    public synchronized void clearAll() {
        if (!toggles.isEmpty() || !deletes.isEmpty() || !premiumNotices.isEmpty()) {
            toggles.clear();
            toggleTimes.clear();
            deletes.clear();
            deleteTimes.clear();
            premiumNotices.clear();
            premiumNoticeTimes.clear();
            persist();
        }
    }

    /**
     * @param name player name
     * @return the queued toggle value, or null if not queued
     */
    public synchronized Boolean getToggle(String name) {
        pruneExpired(System.currentTimeMillis());
        return toggles.get(name);
    }

    /**
     * Whether a premium toggle for this name was queued recently enough to relax
     * the configure-phase connection UUID check. An entry that waited longer still
     * gets relayed, but it no longer reopens that admission exception.
     *
     * @param name player name
     * @param maxAgeMillis maximum accepted age
     * @return true for a queued premium toggle within the window
     */
    public synchronized boolean isRecentPremiumToggle(String name, long maxAgeMillis) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        if (!Boolean.TRUE.equals(toggles.get(name))) {
            return false;
        }
        Long queuedAt = toggleTimes.get(name);
        return queuedAt != null && now - queuedAt <= maxAgeMillis;
    }

    /**
     * @param name player name
     * @return true if a toggle is queued for the name
     */
    public synchronized boolean containsToggle(String name) {
        pruneExpired(System.currentTimeMillis());
        return toggles.containsKey(name);
    }

    /**
     * @param name player name
     * @return true if a delete is queued for the name
     */
    public synchronized boolean containsDelete(String name) {
        pruneExpired(System.currentTimeMillis());
        return deletes.contains(name);
    }

    /**
     * @return snapshot of the queued toggles (copy — safe to iterate)
     */
    public synchronized Map<String, Boolean> toggles() {
        pruneExpired(System.currentTimeMillis());
        return new LinkedHashMap<>(toggles);
    }

    /**
     * @return snapshot of the queued deletes (copy — safe to iterate)
     */
    public synchronized Set<String> deletes() {
        pruneExpired(System.currentTimeMillis());
        return new LinkedHashSet<>(deletes);
    }

    /**
     * @return true if any toggle, delete or premium notice is queued
     */
    public synchronized boolean hasPending() {
        pruneExpired(System.currentTimeMillis());
        return !toggles.isEmpty() || !deletes.isEmpty() || !premiumNotices.isEmpty();
    }

    /**
     * Drops entries older than {@link #MAX_ENTRY_AGE_MILLIS} so a long-forgotten
     * admin action cannot be relayed days later.
     *
     * @param now current time in epoch milliseconds
     * @return true when at least one entry was dropped
     */
    private boolean pruneExpired(long now) {
        long cutoff = now - MAX_ENTRY_AGE_MILLIS;
        int removed = pruneBooleanMap(toggles, toggleTimes, cutoff)
                + pruneNameSet(deletes, deleteTimes, cutoff)
                + pruneBooleanMap(premiumNotices, premiumNoticeTimes, cutoff);
        if (removed > 0) {
            logger.warn("Dropped {} stale pending relay entr{} older than {} hours",
                    removed, removed == 1 ? "y" : "ies", MAX_ENTRY_AGE_MILLIS / 3_600_000L);
        }
        return removed > 0;
    }

    private static int pruneBooleanMap(Map<String, Boolean> values, Map<String, Long> times, long cutoff) {
        int removed = 0;
        for (String name : new ArrayList<>(values.keySet())) {
            if (isExpired(times.get(name), cutoff)) {
                values.remove(name);
                times.remove(name);
                removed++;
            }
        }
        return removed;
    }

    private static int pruneNameSet(Set<String> names, Map<String, Long> times, long cutoff) {
        int removed = 0;
        for (String name : new ArrayList<>(names)) {
            if (isExpired(times.get(name), cutoff)) {
                names.remove(name);
                times.remove(name);
                removed++;
            }
        }
        return removed;
    }

    private static boolean isExpired(Long queuedAt, long cutoff) {
        return queuedAt == null || queuedAt < cutoff;
    }

    private void persist() {
        Data data = new Data();
        data.toggles = new LinkedHashMap<>(toggles);
        data.toggleTimes = new LinkedHashMap<>(toggleTimes);
        data.deletes = new ArrayList<>(deletes);
        data.deleteTimes = new LinkedHashMap<>(deleteTimes);
        data.premiumNotices = new LinkedHashMap<>(premiumNotices);
        data.premiumNoticeTimes = new LinkedHashMap<>(premiumNoticeTimes);

        String json = gson.toJson(data);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            logger.warn("Failed to persist pending relay queue to {}: {}", file, ex.getMessage());
        }
    }

    private static final class Data {
        private Map<String, Boolean> toggles;
        private Map<String, Long> toggleTimes;
        private ArrayList<String> deletes;
        private Map<String, Long> deleteTimes;
        private Map<String, Boolean> premiumNotices;
        private Map<String, Long> premiumNoticeTimes;
    }
}
