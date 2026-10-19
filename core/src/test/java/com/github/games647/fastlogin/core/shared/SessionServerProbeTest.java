/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
 * Pins the reachability probe: it asks the session server (a hasJoined
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
