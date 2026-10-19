/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.task;

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DelayedAuthHookTest {

    @Test
    void createNewReflectiveInstance() throws ReflectiveOperationException {
        DelayedAuthHook authHook = new DelayedAuthHook(null);
        assertNotNull(authHook.newInstance(DummyHook.class));
    }

    public static class DummyHook implements AuthPlugin<Player> {

        @Override
        public boolean forceLogin(Player player) {
            return false;
        }

        @Override
        public boolean forceRegister(Player player, String password) {
            return false;
        }

        @Override
        public boolean isRegistered(String playerName) throws Exception {
            return false;
        }
    }
}
