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
 */
public class PendingRelayStore {

    private static final String FILE_NAME = "pending-relay.json";

    private final Path file;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Boolean> toggles = new ConcurrentHashMap<>();
    private final Set<String> deletes = ConcurrentHashMap.newKeySet();

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
        if (data.toggles != null) {
            toggles.putAll(data.toggles);
        }
        if (data.deletes != null) {
            deletes.addAll(data.deletes);
        }
        return !toggles.isEmpty() || !deletes.isEmpty();
    }

    /**
     * Queues a premium/cracked toggle for later relay and persists it.
     *
     * @param name player name
     * @param activate true for premium, false for cracked
     */
    public synchronized void queueToggle(String name, boolean activate) {
        toggles.put(name, activate);
        persist();
    }

    /**
     * Removes a queued toggle (if present) and persists the removal.
     *
     * @param name player name
     * @return true if the entry existed and was removed
     */
    public synchronized boolean clearToggle(String name) {
        boolean removed = toggles.remove(name) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * Queues a delete for later relay and persists it.
     *
     * @param name player name
     */
    public synchronized void queueDelete(String name) {
        if (deletes.add(name)) {
            persist();
        }
    }

    /**
     * Removes a queued delete (if present) and persists the removal.
     *
     * @param name player name
     * @return true if the entry existed and was removed
     */
    public synchronized boolean clearDelete(String name) {
        boolean removed = deletes.remove(name);
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * Clears all entries and persists the empty queue (used when the proxy
     * support is disabled and queued work can no longer be delivered).
     */
    public synchronized void clearAll() {
        if (!toggles.isEmpty() || !deletes.isEmpty()) {
            toggles.clear();
            deletes.clear();
            persist();
        }
    }

    /**
     * @param name player name
     * @return the queued toggle value, or null if not queued
     */
    public Boolean getToggle(String name) {
        return toggles.get(name);
    }

    /**
     * @param name player name
     * @return true if a toggle is queued for the name
     */
    public boolean containsToggle(String name) {
        return toggles.containsKey(name);
    }

    /**
     * @param name player name
     * @return true if a delete is queued for the name
     */
    public boolean containsDelete(String name) {
        return deletes.contains(name);
    }

    /**
     * @return snapshot of the queued toggles (copy — safe to iterate)
     */
    public Map<String, Boolean> toggles() {
        return new LinkedHashMap<>(toggles);
    }

    /**
     * @return snapshot of the queued deletes (copy — safe to iterate)
     */
    public Set<String> deletes() {
        return new LinkedHashSet<>(deletes);
    }

    /**
     * @return true if any toggle or delete is queued
     */
    public boolean hasPending() {
        return !toggles.isEmpty() || !deletes.isEmpty();
    }

    private void persist() {
        Data data = new Data();
        data.toggles = new LinkedHashMap<>(toggles);
        data.deletes = new ArrayList<>(deletes);

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
        private ArrayList<String> deletes;
    }
}
