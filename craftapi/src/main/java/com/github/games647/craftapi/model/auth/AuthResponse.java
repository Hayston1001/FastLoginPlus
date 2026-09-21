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
package com.github.games647.craftapi.model.auth;

import com.github.games647.craftapi.model.Profile;

import java.util.Objects;
import java.util.UUID;

/**
 * Result after an authentication request.
 */
public class AuthResponse {

    private final UUID accessToken;
    private final Profile selectedProfile;

    /**
     * @param accessToken access token for authentication
     * @param selectedProfile selected game profile
     */
    public AuthResponse(UUID accessToken, Profile selectedProfile) {
        this.accessToken = accessToken;
        this.selectedProfile = selectedProfile;
    }

    /**
     * @return access token for authenticating without a password
     */
    public UUID getAccessToken() {
        return accessToken;
    }

    /**
     * @return in game profile
     */
    public Profile getSelectedProfile() {
        return selectedProfile;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof AuthResponse) {
            AuthResponse that = (AuthResponse) other;
            return Objects.equals(accessToken, that.accessToken)
                    && Objects.equals(selectedProfile, that.selectedProfile);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken, selectedProfile);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "selectedProfile=" + selectedProfile
                + '}';
    }
}
