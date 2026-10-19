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
