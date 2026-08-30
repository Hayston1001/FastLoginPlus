/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
