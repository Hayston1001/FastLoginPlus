/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.conf.Configuration;
import net.md_5.bungee.connection.InitialHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ConnectListenerTest {

    @Test
    void testUUIDSetter() throws Throwable {
        BungeeCord proxyMock = mock(BungeeCord.class);
        BungeeCord.setInstance(proxyMock);
        
        Configuration configMock = mock(Configuration.class);
        Field configField = proxyMock.getClass().getField("config");
        configField.setAccessible(true);
        configField.set(proxyMock, configMock);

        InitialHandler handler = new InitialHandler(proxyMock, null);

        UUID expectedUUID = UUID.randomUUID();
        ConnectListener.UNIQUE_ID_SETTER.invokeExact(handler, expectedUUID);
        assertEquals(expectedUUID, handler.getUniqueId());
    }
}
