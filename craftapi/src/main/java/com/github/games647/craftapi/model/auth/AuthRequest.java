/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.model.auth;

import java.util.Objects;

/**
 * Authentication request from a Minecraft client to the Mojang servers.
 */
public class AuthRequest {

    private final Agent agent = new Agent();

    private final String username;
    private final String password;

    /**
     * @param username email address
     * @param password plain text password
     */
    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * @return email address
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return password as plain text
     */
    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof AuthRequest) {
            AuthRequest that = (AuthRequest) other;
            return Objects.equals(agent, that.agent)
                    && Objects.equals(username, that.username)
                    && Objects.equals(password, that.password);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(agent, username, password);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "agent=" + agent
                + ", username='" + username + '\''
                + '}';
    }
}
