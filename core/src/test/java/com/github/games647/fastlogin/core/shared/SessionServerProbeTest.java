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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.resolver.MojangResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the F-13 reachability probe: it asks the session server (a hasJoined
 * with a nonsense hash), it reads "no such record" as reachable, and it reuses
 * one answer for the cache window so an authentication outage costs a single
 * probe.
 */
class SessionServerProbeTest {

    @Test
    void reachableWhenTheSessionServerAnswers() throws Exception {
        MojangResolver resolver = mock(MojangResolver.class);
        when(resolver.hasJoined(anyString(), anyString(), any())).thenReturn(Optional.empty());

        SessionServerProbe probe = new SessionServerProbe(resolver, 60_000L);

        assertTrue(probe.isReachable(), "an answer of 'no such record' proves the server is up");
    }

    @Test
    void unreachableWhenTheQueryFails() throws Exception {
        MojangResolver resolver = mock(MojangResolver.class);
        when(resolver.hasJoined(anyString(), anyString(), any())).thenThrow(new IOException("connect timed out"));

        SessionServerProbe probe = new SessionServerProbe(resolver, 60_000L);

        assertFalse(probe.isReachable());
    }

    @Test
    void unreachableOnAnUnexpectedExceptionToo() throws Exception {
        // a non-IO failure must degrade to "unreachable", never throw into the
        // settlement sweep and stall it silently
        MojangResolver resolver = mock(MojangResolver.class);
        when(resolver.hasJoined(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("unexpected parser state"));

        SessionServerProbe probe = new SessionServerProbe(resolver, 60_000L);

        assertFalse(probe.isReachable());
    }

    @Test
    void reusesTheCachedAnswerWithinTheWindow() throws Exception {
        MojangResolver resolver = mock(MojangResolver.class);
        when(resolver.hasJoined(anyString(), anyString(), any())).thenReturn(Optional.empty());

        SessionServerProbe probe = new SessionServerProbe(resolver, 60_000L);
        assertTrue(probe.isReachable());
        assertTrue(probe.isReachable());

        verify(resolver, times(1)).hasJoined(anyString(), anyString(), any());
    }
}
