/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.hooks;

/**
 * Password generator for your auth plugin.
 * @param <P> platform dependent player class
 */
@FunctionalInterface
public interface PasswordGenerator<P> {

    /**
     * Generate a password for a non-registered player
     * @param player player representation
     * @return generated password
     */
    String getRandomPassword(P player);
}
