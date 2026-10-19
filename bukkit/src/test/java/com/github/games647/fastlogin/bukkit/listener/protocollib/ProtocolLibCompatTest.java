/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.injector.netty.Injector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProtocolLibCompatTest {

    @Test
    void nonTemporaryPlayerIsReportedInactive() {
        Logger log = mock(Logger.class);

        assertFalse(ProtocolLibCompat.isConnectionActive(log, false, mock(Player.class)));
        // a non-temporary player is the normal "no injector" case — nothing is logged
        verify(log, never()).info(anyString(), any(), any(), any());
    }

    @Test
    void temporaryPlayerWithoutInjectorIsReportedInactive() {
        Logger log = mock(Logger.class);

        assertFalse(ProtocolLibCompat.isConnectionActive(log, false,
                TemporaryPlayerFactory.createTemporaryPlayer()));
        verify(log, never()).info(anyString(), any(), any(), any());
    }

    @Test
    void unresolvedInjectorIsInactiveAndDebugLogged() {
        Logger log = mock(Logger.class);
        Player ghost = TemporaryPlayerFactory.createTemporaryPlayer();
        TemporaryPlayerFactory.setInjectorForPlayer(ghost, mock(Injector.class));

        assertFalse(ProtocolLibCompat.isConnectionActive(log, true, ghost));
        // the cast failure must not vanish silently — it is reported in debug mode
        verify(log).info(anyString(), any(), any(), any());
    }

    @Test
    void injectorAndChannelOfNonTemporaryPlayerAreNull() {
        Logger log = mock(Logger.class);
        Player nonTemporary = mock(Player.class);

        assertNull(ProtocolLibCompat.getInjector(log, false, nonTemporary));
        assertNull(ProtocolLibCompat.getChannel(log, false, nonTemporary));
    }
}
