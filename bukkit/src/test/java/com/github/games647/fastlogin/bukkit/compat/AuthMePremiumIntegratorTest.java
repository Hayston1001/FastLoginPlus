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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the fail-closed gate of the destructive AuthMe cleanup on the
 * cracked-session path (0.5.0/F059): a premium-flagged AuthMe record may only
 * be cleared when FLP's own profile row still exists (stale /cracked retry).
 *
 * <p>Also covers the ISS-02 proxy-sync decision: FLP changes AuthMe's premium state
 * through direct DataSource writes, which bypass the proxy's own premium cache.
 * The ISS-04 UUID gate is pinned here too: AuthMe reads a null {@code premium_uuid}
 * as "not premium", so a null write is destructive rather than inert — and
 * {@code resolvePremiumUuid} decides which fallback source, if any, may replace it.</p>
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

    @Test
    void enabledProxyMustReceiveNotification() {
        // AuthMe resolved its BungeeSender and the bungeecord hook is on → notify.
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.SEND,
            AuthMePremiumIntegrator.decideProxySync(true, true));
    }

    @Test
    void disabledProxyIntegrationMustStaySilent() {
        // Direct-connect server (no proxy): there is no remote cache to sync, so a
        // warning on every cracked toggle would be pure noise.
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.SKIP,
            AuthMePremiumIntegrator.decideProxySync(true, false));
    }

    @Test
    void unresolvableSenderMustWarn() {
        // ISS-02: the notification did not happen. On the cracked path the proxy keeps
        // forcing online-mode for a non-premium player, so the admin must be told.
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.WARN,
            AuthMePremiumIntegrator.decideProxySync(false, false));
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.WARN,
            AuthMePremiumIntegrator.decideProxySync(false, true));
    }

    @Test
    void nullUuidMustNeverBeStamped() {
        // ISS-04: the proxy LOGIN path (BungeeListener.onLoginMessage) builds its session
        // without a UUID, and ForceLoginTask used to forward that null straight into
        // AuthMe. A null premium_uuid means "not premium" to AuthMe, so the write cleared
        // the flag on existing records instead of setting it — while logging success.
        assertFalse(AuthMePremiumIntegrator.isStampablePremiumUuid(null));
    }

    @Test
    void verifiedUuidMayBeStamped() {
        assertTrue(AuthMePremiumIntegrator.isStampablePremiumUuid(UUID.randomUUID()));
    }

    @Test
    void sessionUuidWinsWhenPresent() {
        // Direct mode (VerifyResponseTask) and the Paper configuration phase both put the
        // verified UUID on the session, which is the authoritative source.
        UUID session = UUID.randomUUID();
        assertEquals(session, AuthMePremiumIntegrator.resolvePremiumUuid(
            session, offlineUuid("someone")));
    }

    @Test
    void proxyForwardedConnectionUuidFillsTheGap() {
        // ISS-04 on a Spigot backend: the proxy paths never set a session UUID and there is
        // no configuration phase, so the player's own UUID is the only source left. It is
        // Mojang-issued (v4) because the proxy forwarded the verified one.
        UUID mojang = UUID.randomUUID();
        assertEquals(4, mojang.version());
        assertEquals(mojang, AuthMePremiumIntegrator.resolvePremiumUuid(null, mojang));
    }

    @Test
    void offlineUuidMustNotBeAdopted() {
        // v3 = name-derived offline UUID: premiumUuid:false, a cracked player, or a
        // Floodgate account. Stamping it would advertise premium for an account the proxy
        // never verified.
        UUID offline = offlineUuid("someone");
        assertEquals(3, offline.version());
        assertNull(AuthMePremiumIntegrator.resolvePremiumUuid(null, offline));
    }

    @Test
    void missingConnectionUuidYieldsNothing() {
        assertNull(AuthMePremiumIntegrator.resolvePremiumUuid(null, null));
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
