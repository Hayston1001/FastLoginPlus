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
package com.github.games647.fastlogin.bukkit.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the fail-closed gate of the destructive AuthMe cleanup on the
 * cracked-session path (0.5.0/F059): a premium-flagged AuthMe record may only
 * be cleared when FLP's own profile row still exists (stale /cracked retry).
 */
class AuthMePremiumIntegratorTest {

    @Test
    void staleCrackedRetryShouldBeCleaned() {
        // premium-flagged record + FLP row exists → stale /cracked retry → cleanup
        assertTrue(AuthMePremiumIntegrator.shouldClearPremiumRecord(true, true));
    }

    @Test
    void missingFlpRowMustFailClosed() {
        // premium-flagged record but NO FLP row (DB reset / first login) →
        // deleting would open a registration window for impostors
        assertFalse(AuthMePremiumIntegrator.shouldClearPremiumRecord(true, false));
    }

    @Test
    void nonPremiumRecordShouldNeverBeTouched() {
        assertFalse(AuthMePremiumIntegrator.shouldClearPremiumRecord(false, true));
        assertFalse(AuthMePremiumIntegrator.shouldClearPremiumRecord(false, false));
    }
}
