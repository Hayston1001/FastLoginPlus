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

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the fail-closed gate of the destructive AuthMe cleanup on the cracked-session path: a
 * premium-flagged AuthMe record may only be cleared when FLP's own profile row still exists
 * (stale /cracked retry). Four further contracts are pinned here:
 *
 * <ul>
 *   <li>the proxy-sync decision — FLP's direct DataSource writes bypass the proxy's premium
 *       cache, so they have to be announced to it;</li>
 *   <li>the UUID gate — AuthMe reads a null {@code premium_uuid} as "not premium", so a null
 *       write destroys the flag instead of being inert, and {@code resolvePremiumUuid} decides
 *       which fallback source, if any, may replace it;</li>
 *   <li>the dialog-key change in AuthMe 6.0.1 — both pending response maps moved to a {@code Long}
 *       session id, and the old player-UUID lookup kept compiling (generics are erased) while
 *       always missing. The dual-key replacement is exercised through the same reflective path
 *       production uses, against listener stand-ins matching each AuthMe shape;</li>
 *   <li>the retry around the {@code premium_uuid} write — AuthMe signals that failure by returning
 *       false, so the write is attempted twice before the caller is told it did not land. Getting
 *       this wrong let a half-built row pass as a successful pre-create: logged as success, then
 *       counted as "registered" while AuthMe read it as "registered but not premium".</li>
 * </ul>
 *
 * <p>The refresh tests pin a second cached setting: FLP changes AuthMe's {@code enablePremium} in
 * memory, so every component holding a local copy has to be told. {@code BungeeReceiver} otherwise
 * answers the next {@code proxy.started} from a stale {@code false} and wipes the proxy's premium
 * list. An absent component is silent, a throwing one is reported and swallowed — a throw would
 * reach {@code forceEnablePremium}'s catch and mark the whole integration as failed.</p>
 *
 * <p><b>Coverage boundary.</b> Only the attempt sequence and its return value are pinned here; the
 * wiring around {@code persistPreCreatedPremium} is established by reading, not by these tests.
 * Re-discarding the invoke result would leave this suite green, so the full path is only covered by
 * fault injection against a live server.</p>
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
        // AuthMe resolved its BungeeSender, the bungeecord hook is on and a player is
        // online to carry the message → notify.
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.SEND,
            AuthMePremiumIntegrator.decideProxySync(true, true, true));
    }

    @Test
    void noCarrierMustQueueInsteadOfSending() {
        // AuthMe picks its carrier from the online player list and silently drops
        // the notification when there is none, so sending here would report a sync that
        // never happened. The message must be queued for relay instead.
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.QUEUE,
            AuthMePremiumIntegrator.decideProxySync(true, true, false));
    }

    @Test
    void disabledProxyIntegrationMustStaySilent() {
        // Direct-connect server (no proxy): there is no remote cache to sync, so a
        // warning on every cracked toggle would be pure noise — and queueing is just as
        // pointless, which is why SKIP wins over QUEUE regardless of the online players.
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.SKIP,
            AuthMePremiumIntegrator.decideProxySync(true, false, false));
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.SKIP,
            AuthMePremiumIntegrator.decideProxySync(true, false, true));
    }

    @Test
    void unresolvableSenderMustWarn() {
        // The notification did not happen. On the cracked path the proxy keeps
        // forcing online-mode for a non-premium player, so the admin must be told.
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.WARN,
            AuthMePremiumIntegrator.decideProxySync(false, false, false));
        assertEquals(AuthMePremiumIntegrator.ProxySyncDecision.WARN,
            AuthMePremiumIntegrator.decideProxySync(false, true, true));
    }

    @Test
    void nullUuidMustNeverBeStamped() {
        // ForceLoginTask used to forward a null session UUID straight into AuthMe.
        // A null premium_uuid means "not premium" to AuthMe, so the write cleared the flag
        // on existing records instead of setting it — while logging success.
        // narrowed when null can reach here (the proxy now sends its verified
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
        // On a Spigot backend there is no configuration phase, so when the session
        // carries no UUID the player's own connection UUID is the only source left. It is
        // Mojang-issued (v4) because the proxy forwarded the verified one.
        // makes the proxy send that UUID in the force message too, so this fallback
        // fires less often — it still covers an older proxy and the ProtocolSupport path.
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
        // a non-null session UUID is not proof of a verified identity. AuthMe's
        // Velocity premium handler rewrites GameProfileRequestEvent's profile to the
        // name-derived offline UUID before FLP's listener reads it, so v3 on the
        // session means the proxy forwarded an identity nobody verified. Accepting it
        // stamps a value that AsynchronousJoin's v4 comparison can never match again.
        UUID offline = offlineUuid("someone");
        assertEquals(3, offline.version());
        assertNull(AuthMePremiumIntegrator.resolvePremiumUuid(offline, null));
    }

    @Test
    void offlineSessionUuidStillLetsAVerifiedConnectionUuidThrough() {
        // Rejecting the session UUID must not become a blanket refusal: the fallback is
        // the very mechanism that covers Spigot backends, so a v4 value
        // there must still be adopted.
        UUID offline = offlineUuid("someone");
        UUID mojang = UUID.randomUUID();
        assertEquals(mojang, AuthMePremiumIntegrator.resolvePremiumUuid(offline, mojang));
    }

    @Test
    void offlineUuidIsNotStampable() {
        // the last gate before the value reaches AuthMe's premium_uuid column
        // must reject on version, not merely on null — otherwise it only moves the
        // corruption one step downstream.
        UUID offline = offlineUuid("someone");
        assertEquals(3, offline.version());
        assertFalse(AuthMePremiumIntegrator.isStampablePremiumUuid(offline));
    }

    @Test
    void authMe601DialogIsClosedThroughTheSessionId() throws Exception {
        // AuthMe 6.0.1 re-keyed both response maps from the player UUID to a Long
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
        // Regression evidence for the dialog-key change, pinned so the fix cannot be mistaken
        // for a no-op: this is exactly the lookup the old code performed, and it must miss.
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

    @Test
    void stampSuccessIsNotRetried() {
        // the ordinary path writes once. Asserted so the retry below cannot be
        // mistaken for "always writes twice".
        AtomicInteger attempts = new AtomicInteger();

        assertTrue(AuthMePremiumIntegrator.persistPreCreatedPremium(() -> {
            attempts.incrementAndGet();
            return true;
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void stampFailureIsRetriedOnce() {
        // The failures this guards against (SQLite busy, a dropped MySQL connection) are
        // transient and the write is idempotent — the UUID already sits on the PlayerAuth.
        // The retry has to actually happen, and its result has to be the one returned: a
        // swallowed retry result would make a recovered write look like a failure, which
        // is precisely how the caller ends up suppressing its own proxy notification.
        AtomicInteger attempts = new AtomicInteger();

        assertTrue(AuthMePremiumIntegrator.persistPreCreatedPremium(
            () -> attempts.incrementAndGet() > 1),
            "the retry's result must be the one returned");
        assertEquals(2, attempts.get());
    }

    @Test
    void persistentStampFailureIsReportedAsFailure() {
        // Both attempts fail: the caller has to learn that the row carries no premium UUID,
        // because that is what decides whether the proxy is told the record is premium.
        AtomicInteger attempts = new AtomicInteger();

        assertFalse(AuthMePremiumIntegrator.persistPreCreatedPremium(() -> {
            attempts.incrementAndGet();
            return false;
        }));
        assertEquals(2, attempts.get(), "exactly one retry, not a loop");
    }

    @Test
    void cachedEnablePremiumConsumerIsRefreshedExactlyOnce() {
        // FLP changes enablePremium in memory, but a component that cached the old value
        // keeps answering from that copy until its reload(Settings) is called. BungeeReceiver is
        // the one that answers the next proxy.started — from a stale false it sends an empty
        // premium list, which wipes the proxy's cache of verified players.
        AtomicInteger reloads = new AtomicInteger();
        List<Exception> warnings = new ArrayList<>();

        boolean refreshed = AuthMePremiumIntegrator.refreshCachedEnablePremium(
            RefreshableComponent.class.getName(), StandInSettings.class.getName(),
            new StandInInjector(new RefreshableComponent(reloads)), new StandInSettings(),
            warnings::add);

        assertTrue(refreshed);
        assertEquals(1, reloads.get(), "one refresh per call, not one per lookup");
        assertTrue(warnings.isEmpty(), "a successful refresh is not a warning");
    }

    @Test
    void absentComponentIsNormalRatherThanAWarning() {
        // The component is resolved by class name, and not every AuthMe build ships every
        // component. Warning here would fire on every startup of such a build, so for the caller
        // a missing class has to look like a refresh that had nothing to do.
        List<Exception> warnings = new ArrayList<>();

        boolean refreshed = AuthMePremiumIntegrator.refreshCachedEnablePremium(
            "com.github.games647.fastlogin.bukkit.compat.NoSuchAuthMeComponent",
            StandInSettings.class.getName(), new StandInInjector(null), new StandInSettings(),
            warnings::add);

        assertFalse(refreshed);
        assertTrue(warnings.isEmpty(), "an absent component is not a failure");
    }

    @Test
    void componentTheInjectorNeverCreatedIsNormalRatherThanAWarning() {
        // Second absence shape: the class exists but AuthMe's injector has no instance of it.
        // There is nothing to refresh, and an admin could not act on the warning either.
        List<Exception> warnings = new ArrayList<>();

        boolean refreshed = AuthMePremiumIntegrator.refreshCachedEnablePremium(
            RefreshableComponent.class.getName(), StandInSettings.class.getName(),
            new StandInInjector(null), new StandInSettings(), warnings::add);

        assertFalse(refreshed);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void failingConsumerRefreshIsReportedAndNotPropagated() {
        // This refresh must not become a new way to fail forceEnablePremium, which turns
        // any exception into a false return and marks the whole AuthMe integration as failed.
        // So a component that throws is reported and swallowed — and the cause has to survive as
        // far as the caller, or "the proxy list may be wiped" cannot be told apart from a
        // reflective API change. Reflection wraps the target's throw in InvocationTargetException.
        IllegalStateException failure = new IllegalStateException("injector is gone");
        List<Exception> warnings = new ArrayList<>();

        boolean refreshed = AuthMePremiumIntegrator.refreshCachedEnablePremium(
            ThrowingComponent.class.getName(), StandInSettings.class.getName(),
            new StandInInjector(new ThrowingComponent(failure)), new StandInSettings(),
            warnings::add);

        assertFalse(refreshed);
        assertEquals(1, warnings.size(), "the caller logs exactly this cause");
        assertTrue(warnings.get(0) instanceof InvocationTargetException);
        assertSame(failure, warnings.get(0).getCause());
    }

    @Test
    void theConfiguredReceiverNameResolvesToAnAuthMeComponent() throws Exception {
        // The refresh resolves AuthMe's receiver by class name, so a rename upstream would turn
        // this fix into a silent no-op — the very failure mode it removes. Class.forName is the
        // half of the contract this classpath can check: the AuthMe jar the module compiles
        // against must still carry the class, and it must still be a SettingsDependent. The
        // reload(Settings) signature cannot be checked here — loading AuthMe's Settings needs its
        // own ConfigMe dependency, which is not on this classpath — so that half rests on
        // reading the 6.0.1 sources and on a manual check against a live server.
        Class<?> receiver = Class.forName(AuthMePremiumIntegrator.BUNGEE_RECEIVER_CLASS);
        boolean settingsDependent = false;
        for (Class<?> implemented : receiver.getInterfaces()) {
            if ("fr.xephi.authme.initialization.SettingsDependent".equals(implemented.getName())) {
                settingsDependent = true;
            }
        }
        assertTrue(settingsDependent, receiver + " must implement SettingsDependent");
    }

    /** Mimics an AuthMe component that caches {@code enablePremium} in a field (BungeeReceiver). */
    static final class RefreshableComponent {

        private final AtomicInteger reloads;

        RefreshableComponent(AtomicInteger reloads) {
            this.reloads = reloads;
        }

        public void reload(StandInSettings settings) {
            reloads.incrementAndGet();
        }
    }

    /** Mimics a component whose {@code reload} fails. */
    static final class ThrowingComponent {

        private final RuntimeException failure;

        ThrowingComponent(RuntimeException failure) {
            this.failure = failure;
        }

        public void reload(StandInSettings settings) {
            throw failure;
        }
    }

    /** Mimics AuthMe's {@code Settings} — the one argument of a {@code SettingsDependent.reload}. */
    static final class StandInSettings {
    }

    /** Mimics {@code ch.jalu.injector.Injector} as far as the refresh uses it. */
    static final class StandInInjector {

        private final Object singleton;

        StandInInjector(Object singleton) {
            this.singleton = singleton;
        }

        public Object getSingleton(Class<?> type) {
            return singleton;
        }
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
