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

import java.time.Instant;

import com.github.games647.fastlogin.core.storage.StoredProfile;

/**
 * DTO for a single player entry in the player database API response.
 *
 * <p>Unlike {@link StoredProfile} which relies on Jackson getter auto-discovery,
 * this class explicitly defines every serialized field. This guarantees that
 * {@code displayUuid} (which computes the offline UUID for cracked players)
 * always appears in the JSON output.</p>
 */
public class PlayerEntry {

    // Use public fields — Jackson serializes them directly, no getter discovery needed
    public final String name;
    public final String id;
    public final String displayUuid;
    public final boolean premium;
    public final String floodgate;
    public final String lastIp;
    public final Instant lastLogin;

    /**
     * Creates a player entry from a stored profile.
     *
     * @param p the stored profile
     */
    public PlayerEntry(StoredProfile p) {
        this.name = p.getName();
        this.id = p.getId() != null ? p.getId().toString() : null;
        this.displayUuid = p.getDisplayUuid();
        this.premium = p.isOnlinemodePreferred();
        this.floodgate = p.getFloodgate().name();
        this.lastIp = p.getLastIp();
        this.lastLogin = p.getLastLogin();
    }
}
