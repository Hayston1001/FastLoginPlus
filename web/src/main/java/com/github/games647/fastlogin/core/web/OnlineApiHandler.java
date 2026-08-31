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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;

import io.javalin.http.Context;

/**
 * Handler for the online players API endpoint.
 */
public class OnlineApiHandler {

    private final SQLStorage storage;
    private final Supplier<List<String>> onlinePlayersSupplier;

    public OnlineApiHandler(SQLStorage storage, Supplier<List<String>> onlinePlayersSupplier) {
        this.storage = storage;
        this.onlinePlayersSupplier = onlinePlayersSupplier;
    }

    /**
     * Handle GET /api/online request.
     *
     * @param ctx the Javalin context
     */
    public void handle(Context ctx) {
        // 0.6.0/F030: same null-storage protection as PlayerApiHandler
        if (storage == null) {
            ctx.status(503).json(java.util.Collections.singletonMap("error",
                    "No storage backend configured"));
            return;
        }

        if (onlinePlayersSupplier == null) {
            ctx.json(Collections.emptyList());
            return;
        }

        List<String> onlineNames = onlinePlayersSupplier.get();
        List<OnlinePlayerInfo> result = new ArrayList<>();

        for (String name : onlineNames) {
            StoredProfile profile = storage.loadProfile(name);
            if (profile != null) {
                result.add(new OnlinePlayerInfo(profile));
            }
        }

        ctx.json(result);
    }
}
