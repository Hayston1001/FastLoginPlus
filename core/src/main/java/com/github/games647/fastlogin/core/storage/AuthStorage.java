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

import java.util.UUID;

public interface AuthStorage {

    /**
     * Loads a profile with strict "not found" semantics (0.6.0/F046).
     *
     * <p>Unlike {@link #loadProfile(String)}, which keeps returning a
     * placeholder profile for unknown names (a legacy login-flow contract),
     * this method returns {@code null} for unknown names — WebUI 404
     * branches must never be fed a fake profile.</p>
     *
     * @param name the player name to look up
     * @return the stored profile, or {@code null} when unknown or on a SQL error
     */
    StoredProfile findProfileByName(String name);

    StoredProfile loadProfile(String name);

    StoredProfile loadProfile(UUID uuid);

    void save(StoredProfile playerProfile);

    boolean deleteProfile(String name);

    void close();
}
