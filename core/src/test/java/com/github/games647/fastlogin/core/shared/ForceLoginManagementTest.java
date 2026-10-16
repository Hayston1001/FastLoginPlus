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

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.DefaultPasswordGenerator;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForceLoginManagementTest {

    @ParameterizedTest
    @EnumSource(LoginMethodDecider.AccountState.class)
    void unverifiedFactsNeverAuthenticateAcrossPolicyAndConsentMatrix(
            LoginMethodDecider.AccountState account) throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(authPlugin.inspectAccount("TestUser")).thenReturn(account);
        when(core.getMessage("ownership-conflict")).thenReturn("ownership-conflict");
        for (IdentityClaim.Consent consent : IdentityClaim.Consent.values()) {
            for (boolean autoLogin : new boolean[]{false, true}) {
                TestLoginSession session = new TestLoginSession("TestUser", true, null);
                session.setUuid(UUID.randomUUID()); // even an injected UUID is not verification
                session.setFacts(new IdentityClaim.Facts(false, false, consent, autoLogin, true, false));
                TestForceLoginManagement management = createManagement(session, false);
                management.run();
                assertFalse(management.premiumHandlingAllowed);
                assertEquals(0, management.ackCount);
                if (account == LoginMethodDecider.AccountState.PREMIUM_OWNED
                        || autoLogin && account == LoginMethodDecider.AccountState.PASSWORD_ACCOUNT
                        && consent == IdentityClaim.Consent.YES) {
                    assertEquals("ownership-conflict", management.denied);
                } else {
                    assertNull(management.denied);
                }
            }
        }
        verify(authPlugin, never()).forceLogin(any());
        verify(authPlugin, never()).forceRegister(any(), anyString());
        verify(storage, never()).save(any());
        verify(core, never()).resetVerificationState(anyString());
    }

    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core;
    private PlatformPlugin<Object> plugin;
    private Configuration config;
    private SQLStorage storage;
    private Logger logger;
    private AuthPlugin<Object> authPlugin;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        core = mock(FastLoginCore.class);
        plugin = mock(PlatformPlugin.class);
        config = mock(Configuration.class);
        storage = mock(SQLStorage.class);
        logger = mock(Logger.class);
        authPlugin = mock(AuthPlugin.class);

        doReturn(plugin).when(core).getPlugin();
        when(core.getConfig()).thenReturn(config);
        when(core.getStorage()).thenReturn(storage);
        when(plugin.getLog()).thenReturn(logger);
        // mock FastLoginCore never initializes localeMessages → stub to no-op
        doNothing().when(core).sendLocaleMessage(anyString(), any());
        // plugins that store the generated password keep the interface default
        when(authPlugin.notifyGeneratedPassword()).thenReturn(true);
        // the production save windows run inside withNameLock - the
        // mock must execute the passed runnable so the wrapped saves still happen
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(storage).withNameLock(anyString(), any(Runnable.class));
    }

    // ---- proxy without auth hook: record the observation, never the consent ----

    @Test
    void proxyWithoutAuthHookRecordsObservationWithoutConsent() {
        // Velocity: authPlugin is always null (no setAuthPluginHook call in the
        // velocity module). The proxy verified the session, so the observation
        // must be written locally instead of waiting for a SuccessMessage ack —
        // but this write happens BEFORE the backend decided (rule 7/8 could still
        // reject), so it must never grant the upgrade consent (D-03 E-3).
        UUID premiumUuid = UUID.randomUUID();
        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(premiumUuid);

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        assertFalse(profile.isOnlinemodePreferred(),
                "the pre-decision write must not grant the upgrade consent");
        assertEquals(premiumUuid, profile.getId());
        assertFalse(profile.isSameNameCracked(), "a passing verification refutes the observation");
        verify(storage).save(profile);
        assertEquals(1, mgmt.ackCount, "proxy must still forward the force-login action");
    }

    @Test
    void proxyWriteRecordsPremiumUuidOnStaleCrackedRow() {
        // Locks the null-branch save semantics: a StoredProfile backed by an
        // existing premium=false row gets the premium UUID observation (which is
        // what routes the name through verification on the next login), while the
        // consent bit is left untouched. Reachable via JoinManagement.isNameChanged,
        // which passes an existing row (premium=false, rowId>=0) into
        // requestPremiumLogin when a player changed their name.
        UUID premiumUuid = UUID.randomUUID();
        StoredProfile profile = new StoredProfile(1, null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1",
                java.time.Instant.now());
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(premiumUuid);

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        assertFalse(profile.isOnlinemodePreferred(), "the observation write never flips the consent");
        assertEquals(premiumUuid, profile.getId());
        verify(storage).save(profile);
    }

    // ---- backend with AuthMe 6.0 bypass (forceLogin returns false) ----

    @Test
    void verifiedPremiumLoginSkipsForceLoginWhenAuthMeBypassedAndStillAcKs() throws Exception {
        // AuthMe 6.0: AsynchronousJoin.canBypassWithPremium() authenticates the
        // player before FLP's ForceLoginTask, so forceLogin() returns false.
        // In proxy mode the backend session keeps profile=null — the branch must
        // not NPE and still has to ack the proxy so it persists the premium row.
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(config.get("autoLogin")).thenReturn(true);
        when(config.get("auto-register-unknown")).thenReturn(true);
        when(authPlugin.isRegistered("TestUser")).thenReturn(true);
        when(authPlugin.forceLogin(any())).thenReturn(false);

        TestLoginSession session = new TestLoginSession("TestUser", true, null);
        session.setUuid(UUID.randomUUID());

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        verify(storage, never()).save(any());
        assertEquals(1, mgmt.ackCount, "backends must ack verified premium sessions even when forceLogin fails");
    }

    // ---- regression: successful forceLogin keeps saving + acking ----

    @Test
    void successfulForceLoginSavesAndAcKs() throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(config.get("autoLogin")).thenReturn(true);
        when(config.get("auto-register-unknown")).thenReturn(true);
        when(authPlugin.isRegistered("TestUser")).thenReturn(true);
        when(authPlugin.forceLogin(any())).thenReturn(true);

        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        profile.setSameNameCracked(true);
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(UUID.randomUUID());

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        // the confirmed outcome ("registered as premium") is what records consent
        assertTrue(profile.isOnlinemodePreferred());
        assertFalse(profile.isSameNameCracked(), "a passing verification refutes the observation");
        verify(storage).save(profile);
        assertEquals(1, mgmt.ackCount);
    }

    // ---- R-7 recovery confirms premium ownership and converges the proxy record ----

    @Test
    void premiumOwnedAccountRecoversExplicitNoConsentAndWarns() throws Exception {
        assertPremiumRecovery(IdentityClaim.Consent.NO, true, true);
    }

    @Test
    void premiumOwnedAccountRecoversUnknownConsentAndWarns() throws Exception {
        assertPremiumRecovery(IdentityClaim.Consent.UNKNOWN, true, true);
    }

    @Test
    void alreadyAuthenticatedPremiumRecoveryStillAcknowledgesTheProxy() throws Exception {
        assertPremiumRecovery(IdentityClaim.Consent.NO, false, false);
    }

    @Test
    void consistentPremiumOwnershipDoesNotEmitARecoveryWarning() throws Exception {
        assertPremiumRecovery(IdentityClaim.Consent.YES, true, true);
    }

    @ParameterizedTest
    @EnumSource(IdentityClaim.Consent.class)
    void policyOffDoesNotRecoverPremiumOwnerAndExplainsMissingPassword(IdentityClaim.Consent consent)
            throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(authPlugin.inspectAccount("TestUser"))
                .thenReturn(LoginMethodDecider.AccountState.PREMIUM_OWNED);
        TestLoginSession session = new TestLoginSession("TestUser", true, null);
        session.setUuid(UUID.randomUUID());
        session.setFacts(new IdentityClaim.Facts(false, false, consent, false, true, false));
        TestForceLoginManagement management = createManagement(session, true);
        management.run();
        assertNull(management.denied);
        assertFalse(management.premiumHandlingAllowed);
        assertEquals(0, management.ackCount);
        verify(authPlugin, never()).forceLogin(any());
        verify(authPlugin, never()).forceRegister(any(), anyString());
        verify(storage, never()).save(any());
        verify(core).sendLocaleMessage("premium-policy-disabled", management.player);
        verify(core, never()).sendLocaleMessage(org.mockito.ArgumentMatchers.eq("premium-password-login"), any());
    }

    private void assertPremiumRecovery(IdentityClaim.Consent consent, boolean loginResult,
                                        boolean localProfile) throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(authPlugin.inspectAccount("TestUser"))
                .thenReturn(LoginMethodDecider.AccountState.PREMIUM_OWNED);
        when(authPlugin.forceLogin(any())).thenReturn(loginResult);

        StoredProfile profile = localProfile
                ? new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1") : null;
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(UUID.randomUUID());
        session.setFacts(new IdentityClaim.Facts(false, false, consent, true, false, false));

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        assertNull(mgmt.denied);
        assertTrue(mgmt.premiumHandlingAllowed);
        verify(authPlugin).forceLogin(mgmt.player);
        if (profile != null) {
            assertTrue(profile.isOnlinemodePreferred(), "R-7 confirms premium ownership");
            assertEquals(session.getUuid(), profile.getId());
            verify(storage).save(profile);
        } else {
            verify(storage, never()).save(any());
        }
        assertEquals(1, mgmt.ackCount, "the backend must acknowledge the premium outcome to its proxy");
        String warning = "Recovered verified premium login for {} from the auth plugin's premium account "
                + "despite proxy consent {}; requested proxy resynchronization";
        if (consent != IdentityClaim.Consent.YES) {
            verify(logger).warn(warning, "TestUser", consent);
        } else {
            verify(logger, never()).warn(org.mockito.ArgumentMatchers.eq(warning), any(), any());
        }
    }

    // ---- a cracked join is not an identity reset ----

    @Test
    void passwordFallbackDoesNotMarkPremiumOrAcknowledgeConsent() throws Exception {
        assertPasswordFallback(IdentityClaim.Consent.NO);
    }

    @Test
    void unknownConsentPasswordFallbackAlsoExplainsHowToUpgrade() throws Exception {
        assertPasswordFallback(IdentityClaim.Consent.UNKNOWN);
    }

    private void assertPasswordFallback(IdentityClaim.Consent consent) throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(authPlugin.inspectAccount("TestUser"))
                .thenReturn(LoginMethodDecider.AccountState.PASSWORD_ACCOUNT);
        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(UUID.randomUUID());
        session.setFacts(new IdentityClaim.Facts(false, false, consent, true, false, false));
        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();
        assertNull(mgmt.denied);
        assertFalse(mgmt.premiumHandlingAllowed);
        assertFalse(profile.isOnlinemodePreferred());
        assertEquals(0, mgmt.ackCount);
        verify(storage, never()).save(any());
        verify(authPlugin, never()).forceLogin(any());
        verify(authPlugin, never()).forceRegister(any(), anyString());
        verify(core).sendLocaleMessage("premium-password-login", mgmt.player);
    }

    @Test
    void standaloneWithoutFactsDoesNotSendProxyPasswordExplanation() throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(config.get("autoLogin")).thenReturn(true);
        when(config.get("auto-register-unknown")).thenReturn(true);
        when(authPlugin.isRegistered("TestUser")).thenReturn(true);
        when(authPlugin.forceLogin(any())).thenReturn(true);
        TestLoginSession session = new TestLoginSession("TestUser", true, null);
        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();
        assertFalse(session.isFactsPresent());
        verify(core, never()).sendLocaleMessage(org.mockito.ArgumentMatchers.eq("premium-password-login"), any());
        verify(authPlugin, never()).inspectAccount(anyString());
    }

    @Test
    void unreadableAccountKeepsItsOwnExplanationInsteadOfPasswordUpgradeNotice() throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(authPlugin.inspectAccount("TestUser")).thenThrow(new IllegalStateException("database unavailable"));
        when(core.getMessage("account-state-unavailable")).thenReturn("account-state-unavailable");
        TestLoginSession session = new TestLoginSession("TestUser", true, null);
        session.setFacts(new IdentityClaim.Facts(false, false, IdentityClaim.Consent.UNKNOWN, true, true, false));
        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();
        verify(plugin).sendMessage(mgmt.player, "account-state-unavailable");
        verify(core, never()).sendLocaleMessage(org.mockito.ArgumentMatchers.eq("premium-password-login"), any());
        verify(authPlugin, never()).forceLogin(any());
        assertEquals(0, mgmt.ackCount);
    }

    @Test
    void crackedJoinKeepsRecordObservations() {
        // The name -> premium UUID and the same-name cracked observations are
        // facts about the name; an ordinary offline join neither refutes nor
        // revokes them (only the toggles and a passing verification do).
        UUID premiumUuid = UUID.randomUUID();
        StoredProfile profile = new StoredProfile(1, premiumUuid, "TestUser", false, FloodgateState.FALSE,
                "127.0.0.1", java.time.Instant.now());
        profile.setSameNameCracked(true);
        TestLoginSession session = new TestLoginSession("TestUser", false, profile);

        TestForceLoginManagement mgmt = createManagement(session, false);
        mgmt.run();

        assertFalse(profile.isOnlinemodePreferred());
        assertEquals(premiumUuid, profile.getId(), "the premium UUID observation survives an offline join");
        assertTrue(profile.isSameNameCracked(), "the observation survives an offline join");
        verify(storage).save(profile);
    }

    // ---- a passing verification resets the unverified-attempt streak ----

    @Test
    void verifiedLoginResetsUnverifiedAttempts() {
        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(UUID.randomUUID());

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        verify(core).resetVerificationState("TestUser");
    }

    // ---- the one-time notice for a freshly confirmed observation ----

    @Test
    void crackedJoinDeliversTheOneTimeObservationNotice() {
        StoredProfile profile = new StoredProfile(1, null, "TestUser", false, FloodgateState.FALSE,
                "127.0.0.1", java.time.Instant.now());
        profile.setSameNameCracked(true);
        TestLoginSession session = new TestLoginSession("TestUser", false, profile);
        when(core.consumeSameNameCrackedNotice("TestUser")).thenReturn(true);

        TestForceLoginManagement mgmt = createManagement(session, false);
        mgmt.run();

        verify(core).sendLocaleMessage("same-name-cracked-recorded", (Object) "fakePlayer");
    }

    @Test
    void crackedJoinWithoutQueuedNoticeStaysSilent() {
        StoredProfile profile = new StoredProfile(1, null, "TestUser", false, FloodgateState.FALSE,
                "127.0.0.1", java.time.Instant.now());
        profile.setSameNameCracked(true);
        TestLoginSession session = new TestLoginSession("TestUser", false, profile);
        when(core.consumeSameNameCrackedNotice("TestUser")).thenReturn(false);

        TestForceLoginManagement mgmt = createManagement(session, false);
        mgmt.run();

        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    @Test
    void clearedObservationSkipsTheQueuedNotice() {
        // the record was cleared between the confirmation and this join (for
        // example an administrator ran /flp premium): the stale "recorded as
        // cracked" notice must not be shown
        StoredProfile profile = new StoredProfile(1, null, "TestUser", false, FloodgateState.FALSE,
                "127.0.0.1", java.time.Instant.now());
        TestLoginSession session = new TestLoginSession("TestUser", false, profile);
        when(core.consumeSameNameCrackedNotice("TestUser")).thenReturn(true);

        TestForceLoginManagement mgmt = createManagement(session, false);
        mgmt.run();

        verify(core, never()).sendLocaleMessage(anyString(), any());
    }

    // ---- a fresh cracked join creates the plain record ----

    @Test
    void crackedSessionPersistsPremiumFalseRow() {
        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        TestLoginSession session = new TestLoginSession("TestUser", false, profile);

        TestForceLoginManagement mgmt = createManagement(session, false);
        mgmt.run();

        assertFalse(profile.isOnlinemodePreferred());
        assertNull(profile.getId());
        verify(storage).save(profile);
    }

    // ---- generated password message ----

    @Test
    void generatedPasswordIsAnnouncedWhenTheAuthPluginStoredIt() throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(core.getPasswordGenerator()).thenReturn(new DefaultPasswordGenerator<>());
        when(core.getMessage("auto-register")).thenReturn("Password: %password");
        when(config.get("autoLogin")).thenReturn(true);
        when(config.get("auto-register-unknown")).thenReturn(true);
        when(authPlugin.isRegistered("TestUser")).thenReturn(false);
        when(authPlugin.forceRegister(any(), anyString())).thenReturn(true);
        when(authPlugin.notifyGeneratedPassword()).thenReturn(true);

        TestLoginSession session = new TestLoginSession("TestUser", true, null);
        session.setUuid(UUID.randomUUID());
        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(plugin).sendMessage(any(), message.capture());
        assertFalse(message.getValue().contains("%password"),
                "the placeholder must be replaced with the generated password");
        assertTrue(message.getValue().startsWith("Password: "));
    }

    @Test
    void generatedPasswordIsNotAnnouncedWhenTheAuthPluginDidNotStoreIt() throws Exception {
        // AuthMe 6.0's takeover pre-creates the record with an empty password hash: announcing
        // the generated password would promise one that the database does not have.
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(core.getPasswordGenerator()).thenReturn(new DefaultPasswordGenerator<>());
        when(core.getMessage("auto-register")).thenReturn("Password: %password");
        when(config.get("autoLogin")).thenReturn(true);
        when(config.get("auto-register-unknown")).thenReturn(true);
        when(authPlugin.isRegistered("TestUser")).thenReturn(false);
        when(authPlugin.forceRegister(any(), anyString())).thenReturn(true);
        when(authPlugin.notifyGeneratedPassword()).thenReturn(false);

        TestLoginSession session = new TestLoginSession("TestUser", true, null);
        session.setUuid(UUID.randomUUID());
        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        verify(authPlugin).forceRegister(any(), anyString());
        verify(plugin, never()).sendMessage(any(), anyString());
    }

    // ---- F-15 A: auto-register-unknown gates the "no local account" cell ----

    @Test
    void standaloneGuidesRegistrationWhenAutoRegisterUnknownIsOff() throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(config.get("autoLogin")).thenReturn(true);
        when(config.get("auto-register-unknown")).thenReturn(false);
        when(authPlugin.isRegistered("TestUser")).thenReturn(false);

        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(UUID.randomUUID());

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        verify(authPlugin, never()).forceRegister(any(), anyString());
        verify(authPlugin, never()).forceLogin(any());
        verify(storage, never()).save(any());
        assertEquals(0, mgmt.ackCount, "guide register is not a confirmed premium outcome");
    }

    @Test
    void standaloneAutoRegistersWhenAutoRegisterUnknownIsOn() throws Exception {
        when(core.getAuthPluginHook()).thenReturn(authPlugin);
        when(core.getPasswordGenerator()).thenReturn(new DefaultPasswordGenerator<>());
        when(config.get("autoLogin")).thenReturn(true);
        when(config.get("auto-register-unknown")).thenReturn(true);
        when(authPlugin.isRegistered("TestUser")).thenReturn(false);
        when(authPlugin.forceRegister(any(), anyString())).thenReturn(true);

        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(UUID.randomUUID());

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        verify(authPlugin).forceRegister(any(), anyString());
    }

    @SuppressWarnings("unchecked")
    private TestForceLoginManagement createManagement(TestLoginSession session, boolean onlineMode) {
        return new TestForceLoginManagement(core, (Object) "fakePlayer", session, onlineMode);
    }

    private static class TestLoginSession extends LoginSession {
        TestLoginSession(String username, boolean registered, StoredProfile profile) {
            super(username, registered, profile);
        }
    }

    private static class TestForceLoginManagement
            extends ForceLoginManagement<Object, Object, TestLoginSession, PlatformPlugin<Object>> {

        private final boolean onlineMode;
        private int ackCount;

        TestForceLoginManagement(FastLoginCore<Object, Object, PlatformPlugin<Object>> core,
                                 Object player, TestLoginSession session, boolean onlineMode) {
            super(core, player, session);
            this.onlineMode = onlineMode;
        }

        @Override
        public FastLoginAutoLoginEvent callFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile) {
            return mock(FastLoginAutoLoginEvent.class);
        }

        @Override
        public void onForceActionSuccess(LoginSession session) {
            ackCount++;
        }

        @Override
        public String getName(Object player) {
            return "TestUser";
        }

        @Override
        public boolean isOnline(Object player) {
            return true;
        }

        @Override
        public boolean isOnlineMode() {
            return onlineMode;
        }

        @Override
        public void denyLogin(Object player, String message) {
            denied = message;
        }

        private String denied;
    }
}
