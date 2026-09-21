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
 * Represents a logged in Mojang account.
 */
public class Account {

    private final Profile profile;
    private final UUID accessToken;

    /**
     * Creates a new Mojang account.
     *
     * @param profile the selected game profile
     * @param accessToken access token for authentication instead of the password
     */
    public Account(Profile profile, UUID accessToken) {
        this.profile = profile;
        this.accessToken = accessToken;
    }

    /**
     * @return Minecraft profile that associated this account
     */
    public Profile getProfile() {
        return profile;
    }

    /**
     * @return access token for authentication against Mojang servers
     */
    public UUID getAccessToken() {
        return accessToken;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Account) {
            Account account = (Account) other;
            return Objects.equals(profile, account.profile)
                    && Objects.equals(accessToken, account.accessToken);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile, accessToken);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "profile=" + profile
                + '}';
    }
}
