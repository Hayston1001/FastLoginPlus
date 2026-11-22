/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import de.st_ddt.crazylogin.CrazyLogin;
import de.st_ddt.crazylogin.listener.PlayerListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrazyLoginHookTest {

    @Test
    void findsHostPlayerListenerWithoutProtocolLib() throws NoSuchFieldException {
        assertEquals(PlayerListener.class, CrazyLoginHook.resolveListenerField(CrazyLogin.class).getType());
    }

}
