/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.hooks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPasswordGeneratorTest {

    @Test
    void smokeTestPassword() {
        PasswordGenerator<?> passwordGenerator = new DefaultPasswordGenerator<>();
        assertTrue(passwordGenerator.getRandomPassword(null).length() == 8);
    }
}
