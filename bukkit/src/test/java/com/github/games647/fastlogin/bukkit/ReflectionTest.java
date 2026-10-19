/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.process.Management;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReflectionTest {

    @Test
    void testAuthMeManagementField() {
        FieldAccessor accessor = Accessors.getFieldAccessor(AuthMeApi.class, Management.class, true);
        assertNotNull(accessor.getField());
    }
}
