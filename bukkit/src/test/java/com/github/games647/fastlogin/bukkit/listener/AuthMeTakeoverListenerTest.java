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
package com.github.games647.fastlogin.bukkit.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the AuthMe reload matcher that drives the premium-takeover re-assert (ISS-32).
 *
 * <p>The matcher has to recognise every spelling the server accepts — the console form without a
 * leading slash, the {@code authme:authme} namespace form CraftBukkit registers for every plugin
 * command, case and extra whitespace — while ignoring everything else, in particular the server's
 * own {@code /reload} and another plugin's command that merely ends in {@code :authme}. A matcher
 * that quietly stops matching is exactly as good as no listener at all: the re-assert would stop
 * happening and nothing else would say so.
 *
 * <p><b>Coverage boundary.</b> What is pinned here is the decision
 * ({@link AuthMeTakeoverListener#isAuthMeReload}). The three handlers around it — one per event,
 * each asking the plugin to re-assert and passing a reason string — need a live
 * {@code FastLoginBukkit} to construct and are ensured by reading them, like the command guard's
 * handlers.
 */
class AuthMeTakeoverListenerTest {

    @Test
    void recognisesEveryAcceptedSpelling() {
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("/authme reload"));
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("authme reload"));
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("/AUTHME RELOAD"));
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("/Authme Reload"));
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("/authme:authme reload"));
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("/AUTHME:AUTHME RELOAD"));
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("  /authme   reload  "));
        // trailing arguments are tolerated: AuthMe's command framework decides whether it accepts
        // them, and a redundant re-assert costs nothing while a missed one would go unnoticed
        assertTrue(AuthMeTakeoverListener.isAuthMeReload("/authme reload now"));
    }

    @Test
    void ignoresEverythingElse() {
        // the server's own reload re-enables plugins, which runs FLP's startup takeover again
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/reload"));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/reload confirm"));
        // AuthMe's command without the reload subcommand, and near misses
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/authme"));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/authme reloadx"));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/authme unregister reload"));
        // another plugin's command that ends with the same label
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/other:authme reload"));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/authme.sub:authme reload"));
        // AuthMe's other commands
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/authme:premium"));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("/premium"));
    }

    @Test
    void toleratesMalformedInput() {
        assertFalse(AuthMeTakeoverListener.isAuthMeReload(null));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload(""));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("   "));
        assertFalse(AuthMeTakeoverListener.isAuthMeReload("reload"));
    }
}
