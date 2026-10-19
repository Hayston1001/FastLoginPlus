/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
