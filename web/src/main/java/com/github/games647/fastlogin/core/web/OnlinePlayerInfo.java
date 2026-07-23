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

import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.StoredProfile;

/**
 * DTO representing an online player with their login type information.
 */
public class OnlinePlayerInfo {

    private final String name;
    private final String uuid;
    private final String type;
    private final String lastIp;
    private final Instant lastLogin;

    public OnlinePlayerInfo(StoredProfile profile) {
        this.name = profile.getName();
        this.uuid = profile.getDisplayUuid();
        this.type = determineLoginType(profile);
        this.lastIp = profile.getLastIp();
        this.lastLogin = profile.getLastLogin();
    }

    private String determineLoginType(StoredProfile profile) {
        FloodgateState floodgate = profile.getFloodgate();

        if (floodgate == FloodgateState.TRUE || floodgate == FloodgateState.LINKED) {
            return "bedrock";
        }

        if (floodgate == FloodgateState.NOT_MIGRATED) {
            return "unknown";
        }

        // floodgate == FALSE
        if (profile.isOnlinemodePreferred()) {
            return "premium";
        } else {
            return "cracked";
        }
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    public String getType() {
        return type;
    }

    public String getLastIp() {
        return lastIp;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }
}
