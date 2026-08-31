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
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        // 0.5.0/F020: the production save windows run inside withNameLock - the
        // mock must execute the passed runnable so the wrapped saves still happen
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(storage).withNameLock(anyString(), any(Runnable.class));
    }

    // ---- proxy without auth hook: persist the premium row locally ----

    @Test
    void proxyWithoutAuthHookPersistsVerifiedPremiumRow() {
        // Velocity: authPlugin is always null (no setAuthPluginHook call in the
        // velocity module). The proxy verified the session, so the row must be
        // written locally instead of waiting for a SuccessMessage ack.
        UUID premiumUuid = UUID.randomUUID();
        StoredProfile profile = new StoredProfile(null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1");
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(premiumUuid);

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        assertTrue(profile.isOnlinemodePreferred());
        assertEquals(premiumUuid, profile.getId());
        verify(storage).save(profile);
        assertEquals(1, mgmt.ackCount, "proxy must still forward the force-login action");
    }

    @Test
    void proxyUpgradesStaleCrackedRowToPremium() {
        // Locks the null-branch save semantics: a StoredProfile backed by an
        // existing premium=false row is upgraded (never demoted) on a verified
        // premium session. Reachable via JoinManagement.isNameChanged, which
        // passes an existing row (premium=false, rowId>=0) into
        // requestPremiumLogin when a player changed their name.
        UUID premiumUuid = UUID.randomUUID();
        StoredProfile profile = new StoredProfile(1, null, "TestUser", false, FloodgateState.FALSE, "127.0.0.1",
                java.time.Instant.now());
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(premiumUuid);

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        assertTrue(profile.isOnlinemodePreferred());
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
        TestLoginSession session = new TestLoginSession("TestUser", true, profile);
        session.setUuid(UUID.randomUUID());

        TestForceLoginManagement mgmt = createManagement(session, true);
        mgmt.run();

        assertTrue(profile.isOnlinemodePreferred());
        verify(storage).save(profile);
        assertEquals(1, mgmt.ackCount);
    }

    // ---- regression: cracked sessions keep persisting premium=false ----

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
    }
}
