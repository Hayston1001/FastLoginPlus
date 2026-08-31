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
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

/**
 * 0.5.0/R2: the EventTask continuation must be resumed exactly once on every
 * path — including when the anti-bot decision application throws (previously
 * the login hung until the read timeout).
 */
class ConnectListenerResumeOnceTest {

    @Test
    void resumeOnceResumesOnlyOnFirstCall() {
        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        ConnectListener.resumeOnce(continuation, resumed);
        ConnectListener.resumeOnce(continuation, resumed);
        ConnectListener.resumeOnce(continuation, resumed);

        verify(continuation, times(1)).resume();
        assertTrue(resumed.get());
    }

    @Test
    void healthyDecisionResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));

        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        listener.applyDecisionSafely(mock(PreLoginEvent.class), mock(InboundConnection.class),
                "Steve", Action.Ignore, continuation, resumed);

        verify(continuation, times(1)).resume();
        assertTrue(resumed.get());
    }

    @Test
    void throwingDecisionStillResumesExactlyOnce() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore<?, ?, ?> core = mock(FastLoginCore.class);
        doReturn(core).when(plugin).getCore();
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        // Block action path: the locale lookup blows up
        when(core.getMessage(anyString())).thenThrow(new IllegalStateException("boom"));
        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));

        Continuation continuation = mock(Continuation.class);
        AtomicBoolean resumed = new AtomicBoolean(false);

        listener.applyDecisionSafely(mock(PreLoginEvent.class), mock(InboundConnection.class),
                "Steve", Action.Block, continuation, resumed);

        verify(continuation, times(1)).resume();
        assertTrue(resumed.get(), "the guard must stay flipped after the fallback resume");
    }

    @Test
    void freshGuardStartsUnresumed() {
        assertFalse(new AtomicBoolean(false).get());
    }
}
