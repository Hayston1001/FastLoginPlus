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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <p>The ISS-06 tests pin the AuthMe 6.0.1 dialog key change: both pending response maps
 * moved from a player-UUID key to a {@code Long} session id, so the old lookup kept
 * compiling (generics are erased at runtime) and kept running while always missing. The
 * dual-key lookup that replaces it is exercised through the same reflective path
 * production uses, against listener stand-ins matching each AuthMe shape.</p>
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
        // ISS-04: ForceLoginTask used to forward a null session UUID straight into AuthMe.
        // A null premium_uuid means "not premium" to AuthMe, so the write cleared the flag
        // on existing records instead of setting it — while logging success.
        // 0.7.0/F10 narrowed when null can reach here (the proxy now sends its verified
        // UUID), but "the proxy verified nothing" is still a normal state, so the guard stays.
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
        // ISS-04 on a Spigot backend: there is no configuration phase, so when the session
        // carries no UUID the player's own connection UUID is the only source left. It is
        // Mojang-issued (v4) because the proxy forwarded the verified one.
        // 0.7.0/F10 makes the proxy send that UUID in the force message too, so this fallback
        // fires less often — it still covers an older proxy, and ISS-29.
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

    @Test
    void offlineSessionUuidMustNotBeAdopted() {
        // ISS-25: a non-null session UUID is not proof of a verified identity. AuthMe's
        // Velocity premium handler rewrites GameProfileRequestEvent's profile to the
        // name-derived offline UUID before FLP's listener reads it (ISS-07), so v3 on the
        // session means the proxy forwarded an identity nobody verified. Accepting it
        // stamps a value that AsynchronousJoin's v4 comparison can never match again.
        UUID offline = offlineUuid("someone");
        assertEquals(3, offline.version());
        assertNull(AuthMePremiumIntegrator.resolvePremiumUuid(offline, null));
    }

    @Test
    void offlineSessionUuidStillLetsAVerifiedConnectionUuidThrough() {
        // Rejecting the session UUID must not become a blanket refusal: the fallback is
        // the very mechanism that covers Spigot backends (ISS-04 / T6), so a v4 value
        // there must still be adopted.
        UUID offline = offlineUuid("someone");
        UUID mojang = UUID.randomUUID();
        assertEquals(mojang, AuthMePremiumIntegrator.resolvePremiumUuid(offline, mojang));
    }

    @Test
    void offlineUuidIsNotStampable() {
        // ISS-25: the last gate before the value reaches AuthMe's premium_uuid column
        // must reject on version, not merely on null — otherwise it only moves the
        // corruption one step downstream.
        UUID offline = offlineUuid("someone");
        assertEquals(3, offline.version());
        assertFalse(AuthMePremiumIntegrator.isStampablePremiumUuid(offline));
    }

    @Test
    void authMe601DialogIsClosedThroughTheSessionId() throws Exception {
        // ISS-06: AuthMe 6.0.1 re-keyed both response maps from the player UUID to a Long
        // session id held in a separate connectionSessions map. The old Map<UUID, ...>
        // cast still compiled and still ran, but get(playerUuid) always missed — leaving
        // the dialog open until AuthMe's own 30s timeout, with no exception and no log.
        SessionKeyedListener listener = new SessionKeyedListener();
        Object connection = new Object();
        UUID playerId = UUID.randomUUID();
        CompletableFuture<String> pending = new CompletableFuture<>();
        listener.connectionSessions.put(connection, 42L);
        listener.pendingRegisterResponses.put(42L, pending);

        assertEquals(42L, AuthMePremiumIntegrator.completePendingDialog(
            SessionKeyedListener.class, listener, "pendingRegisterResponses",
            connection, playerId));
        assertTrue(pending.isDone());
        assertNull(pending.join());
    }

    @Test
    void preFixUuidLookupWouldHaveMissedTheSixOhOneDialog() {
        // Regression evidence for ISS-06, pinned so the fix cannot be mistaken for a
        // no-op: this is exactly the lookup the old code performed, and it must miss.
        SessionKeyedListener listener = new SessionKeyedListener();
        Object connection = new Object();
        UUID playerId = UUID.randomUUID();
        CompletableFuture<String> pending = new CompletableFuture<>();
        listener.connectionSessions.put(connection, 42L);
        listener.pendingRegisterResponses.put(42L, pending);

        assertNull(listener.pendingRegisterResponses.get(playerId));
        assertFalse(pending.isDone());
    }

    @Test
    void authMe600DialogIsStillClosedThroughThePlayerUuid() throws Exception {
        // The 6.0.0 shape has no connectionSessions field at all and keeps the response
        // maps UUID-keyed. Both versions ship in the wild, so this path must survive.
        LegacyKeyedListener listener = new LegacyKeyedListener();
        UUID playerId = UUID.randomUUID();
        CompletableFuture<String> pending = new CompletableFuture<>();
        listener.pendingLoginResponses.put(playerId, pending);

        assertEquals(playerId, AuthMePremiumIntegrator.completePendingDialog(
            LegacyKeyedListener.class, listener, "pendingLoginResponses",
            new Object(), playerId));
        assertTrue(pending.isDone());
    }

    @Test
    void uuidLookupIsTheFallbackWhenNoSessionIsOpen() throws Exception {
        // 6.0.1 before its handler registered the connection, and 6.0.0 servers, both land
        // here: no session id, so the UUID key is tried. A miss is harmless —
        // ConcurrentHashMap.get returns null for a key of an unrelated type.
        SessionKeyedListener listener = new SessionKeyedListener();
        UUID playerId = UUID.randomUUID();
        CompletableFuture<String> pending = new CompletableFuture<>();
        listener.pendingLoginResponses.put(playerId, pending);

        assertEquals(playerId, AuthMePremiumIntegrator.completePendingDialog(
            SessionKeyedListener.class, listener, "pendingLoginResponses",
            new Object(), playerId));
        assertTrue(pending.isDone());
    }

    @Test
    void missingConnectionStillClosesThroughTheUuid() throws Exception {
        // Defensive: when the connection object could not be extracted from the configure
        // event, the lookup must degrade to the UUID key rather than give up.
        LegacyKeyedListener listener = new LegacyKeyedListener();
        UUID playerId = UUID.randomUUID();
        CompletableFuture<String> pending = new CompletableFuture<>();
        listener.pendingRegisterResponses.put(playerId, pending);

        assertNull(AuthMePremiumIntegrator.resolveDialogSessionId(
            LegacyKeyedListener.class, listener, null));
        assertEquals(playerId, AuthMePremiumIntegrator.completePendingDialog(
            LegacyKeyedListener.class, listener, "pendingRegisterResponses", null, playerId));
        assertTrue(pending.isDone());
    }

    @Test
    void absentDialogIsInertRatherThanDestructive() throws Exception {
        // No pending dialog registered: both keys miss and nothing is completed. The worst
        // case of the dual-key lookup therefore equals the pre-fix behaviour — a dialog
        // that times out, never a completion of somebody else's future.
        SessionKeyedListener listener = new SessionKeyedListener();
        assertNull(AuthMePremiumIntegrator.completePendingDialog(
            SessionKeyedListener.class, listener, "pendingRegisterResponses",
            new Object(), UUID.randomUUID()));
        assertNull(AuthMePremiumIntegrator.completePendingDialog(
            SessionKeyedListener.class, listener, "pendingLoginResponses",
            new Object(), UUID.randomUUID()));
    }

    @Test
    void nonLongSessionValueIsIgnored() {
        // A future AuthMe refactor to a different session id type must degrade to the UUID
        // path, not to completing an unrelated future.
        SessionKeyedListener listener = new SessionKeyedListener();
        Object connection = new Object();
        listener.connectionSessions.put(connection, "not-an-id");

        assertNull(AuthMePremiumIntegrator.resolveDialogSessionId(
            SessionKeyedListener.class, listener, connection));
    }

    @Test
    void dialogFutureOnlyAcceptsAFuture() {
        assertFalse(AuthMePremiumIntegrator.completeDialogFuture(null));
        assertFalse(AuthMePremiumIntegrator.completeDialogFuture("not a future"));
        CompletableFuture<String> future = new CompletableFuture<>();
        assertTrue(AuthMePremiumIntegrator.completeDialogFuture(future));
        assertNull(future.join());
    }

    /** Mimics AuthMe 6.0.1: response maps keyed by a {@code Long} session id. */
    private static final class SessionKeyedListener {

        private final Map<Object, CompletableFuture<String>> pendingLoginResponses =
            new ConcurrentHashMap<>();
        private final Map<Object, CompletableFuture<String>> pendingRegisterResponses =
            new ConcurrentHashMap<>();
        private final Map<Object, Object> connectionSessions = new ConcurrentHashMap<>();
    }

    /** Mimics AuthMe 6.0.0: response maps UUID-keyed, no session registry at all. */
    private static final class LegacyKeyedListener {

        private final Map<Object, CompletableFuture<String>> pendingLoginResponses =
            new ConcurrentHashMap<>();
        private final Map<Object, CompletableFuture<String>> pendingRegisterResponses =
            new ConcurrentHashMap<>();
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
