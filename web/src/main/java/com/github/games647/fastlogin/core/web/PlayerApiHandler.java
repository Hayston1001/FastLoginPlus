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
package com.github.games647.fastlogin.core.web;

import static com.github.games647.fastlogin.core.web.JsonUtil.of;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;

import io.javalin.http.Context;

/**
 * Handler for the player database CRUD API endpoints.
 */
public class PlayerApiHandler {

    private final SQLStorage storage;
    private final PremiumToggleListener toggleListener;

    public PlayerApiHandler(SQLStorage storage) {
        this.storage = storage;
        this.toggleListener = null;
    }

    /**
     * Creates a handler with a premium toggle listener for kick-on-toggle support.
     *
     * @param storage        the storage backend
     * @param toggleListener callback invoked after premium status is toggled via the WebUI
     */
    public PlayerApiHandler(SQLStorage storage, PremiumToggleListener toggleListener) {
        this.storage = storage;
        this.toggleListener = toggleListener;
    }

    /**
     * Handle GET /api/players request (list with optional search and pagination).
     *
     * @param ctx the Javalin context
     */
    public void handleList(Context ctx) {
        String query = ctx.queryParam("q");
        int page = parseIntParam(ctx, "page", 1);
        int size = parseIntParam(ctx, "size", 20);

        // Clamp values
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 1;
        }
        if (size > 100) {
            size = 100;
        }

        int offset = (page - 1) * size;

        List<StoredProfile> profiles;
        int total;

        if (query != null && !query.isEmpty()) {
            profiles = storage.searchProfiles(query, offset, size);
            total = storage.countProfiles(query);
        } else {
            profiles = storage.loadAllProfiles(offset, size);
            total = storage.countProfiles(null);
        }

        // Convert to explicit DTOs so displayUuid is guaranteed in JSON output
        List<PlayerEntry> players = new java.util.ArrayList<>();
        for (StoredProfile p : profiles) {
            players.add(new PlayerEntry(p));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("players", players);
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (total + size - 1) / size);

        ctx.json(response);
    }

    /**
     * Handle GET /api/players/:name request.
     *
     * @param ctx the Javalin context
     */
    public void handleGet(Context ctx) {
        String name = ctx.pathParam("name");
        StoredProfile profile = storage.loadProfile(name);

        if (profile == null) {
            ctx.status(404).json(of("error", "Player not found"));
            return;
        }

        ctx.json(profile);
    }

    /**
     * Handle PUT /api/players/:name/premium or /api/players/:name/cracked request.
     *
     * <p>When a platform toggle listener is registered, the platform performs
     * the complete toggle operation — the same flow used by the
     * {@code /premium} and {@code /cracked} commands: relay to the proxy
     * (with offline queueing when no player can relay the message) or local
     * database update, toggle event and kick. The WebUI never writes the
     * database directly in that mode.</p>
     *
     * @param ctx     the Javalin context
     * @param premium true to set as premium, false to set as cracked
     */
    public void handleSetPremium(Context ctx, boolean premium) {
        String name = ctx.pathParam("name");

        // In proxy setups the backend may have no local database (storage is
        // null there); the existence check is skipped and the platform
        // listener — which relays to the proxy — is the authority.
        StoredProfile profile = null;
        if (storage != null) {
            profile = storage.loadProfile(name);
            if (profile == null) {
                ctx.status(404).json(of("error", "Player not found"));
                return;
            }
        }

        if (toggleListener != null) {
            // Platform performs the full toggle exactly like the commands
            toggleListener.onPremiumToggle(name, premium);
        } else if (profile != null) {
            // Fallback for embedders without a platform listener: direct DB write
            profile.setOnlinemodePreferred(premium);

            // When switching to cracked (offline mode), clear the premium UUID
            // so the player uses offline-mode UUID on next login
            if (!premium) {
                profile.setId(null);
            }

            storage.save(profile);
        } else {
            ctx.status(500).json(of("error", "No storage backend configured"));
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("name", name);
        response.put("premium", premium);

        ctx.json(response);
    }

    /**
     * Handle DELETE /api/players/:name request.
     *
     * <p>Only allows deletion of non-premium (cracked) players.</p>
     *
     * @param ctx the Javalin context
     */
    public void handleDelete(Context ctx) {
        String name = ctx.pathParam("name");
        StoredProfile profile = storage.loadProfile(name);

        if (profile == null) {
            ctx.status(404).json(of("error", "Player not found"));
            return;
        }

        // Only allow deleting cracked players
        if (profile.isOnlinemodePreferred()) {
            ctx.status(400).json(of("error", "Cannot delete premium players"));
            return;
        }

        boolean deleted = storage.deleteProfile(name);

        if (deleted) {
            ctx.json(of("success", true, "name", name));
        } else {
            ctx.status(500).json(of("error", "Failed to delete player"));
        }
    }

    private int parseIntParam(Context ctx, String param, int defaultValue) {
        String value = ctx.queryParam(param);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
