/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import de.st_ddt.crazylogin.CrazyLogin;
import de.st_ddt.crazylogin.listener.PlayerListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CrazyLoginHookTest {

    @Test
    void testPlayerListener() {
        FieldAccessor accessor = Accessors.getFieldAccessor(CrazyLogin.class, PlayerListener.class, true);
        assertNotNull(accessor.getField());
    }

}
