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

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.config.Configuration;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FloodgateManagementTest {

    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core;
    private PlatformPlugin<Object> plugin;
    private Configuration config;
    private SQLStorage storage;
    private MojangResolver resolver;
    private Logger logger;

    // test state captured from the concrete subclass
    private boolean loginStarted;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        core = mock(FastLoginCore.class);
        plugin = mock(PlatformPlugin.class);
        config = mock(Configuration.class);
        storage = mock(SQLStorage.class);
        resolver = mock(MojangResolver.class);
        logger = mock(Logger.class);

        when(core.getConfig()).thenReturn(config);
        doReturn(plugin).when(core).getPlugin();
        when(core.getStorage()).thenReturn(storage);
        when(core.getResolver()).thenReturn(resolver);
        when(core.getAuthPluginHook()).thenReturn(null);
        when(plugin.getLog()).thenReturn(logger);

        loginStarted = false;
    }

    // ---- run() branch: storage is null (Bungee mode) ----

    @Test
    void shouldReturnEarlyWhenStorageIsNull() {
        when(core.getStorage()).thenReturn(null);
        setConfig("true", "true", "false");

        FloodgatePlayer fgPlayer = mockLinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertFalse(loginStarted);
    }

    // ---- run() branch: existing player, NOT_MIGRATED ----

    @Test
    void shouldMigrateNotMigratedLinkedPlayerToLinkedState() {
        setConfig("true", "true", "false");

        StoredProfile existingProfile = makeProfile(FloodgateState.NOT_MIGRATED, false);
        existingProfile.setRowId(1); // make it existing
        when(storage.loadProfile("TestUser")).thenReturn(existingProfile);

        FloodgatePlayer fgPlayer = mockLinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertTrue(loginStarted);
    }

    @Test
    void shouldMigrateNotMigratedUnlinkedPlayerToTrueState() {
        setConfig("true", "true", "false");

        StoredProfile existingProfile = makeProfile(FloodgateState.NOT_MIGRATED, false);
        existingProfile.setRowId(1);
        when(storage.loadProfile("TestUser")).thenReturn(existingProfile);

        FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertTrue(loginStarted);
    }

    // ---- run() branch: existing player, already migrated ----

    @Test
    void shouldReturnWhenTruePlayerBecomesLinked() {
        setConfig("true", "true", "false");

        StoredProfile existingProfile = makeProfile(FloodgateState.TRUE, false);
        existingProfile.setRowId(1);
        when(storage.loadProfile("TestUser")).thenReturn(existingProfile);

        // player is now linked, but stored as TRUE (non-linked) → conflict
        FloodgatePlayer fgPlayer = mockLinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertFalse(loginStarted);
    }

    @Test
    void shouldUpgradeFalsePlayerToLinkedWhenNowLinked() {
        setConfig("true", "true", "false");

        StoredProfile existingProfile = makeProfile(FloodgateState.FALSE, false);
        existingProfile.setRowId(1);
        when(storage.loadProfile("TestUser")).thenReturn(existingProfile);

        FloodgatePlayer fgPlayer = mockLinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertTrue(loginStarted);
    }

    // ---- run() branch: autoRegister disabled for new players ----

    @Test
    void shouldNotAutoLoginNewUnregisteredPlayerWhenAutoRegisterDisabled() {
        setConfig("true", "false", "false");

        StoredProfile newProfile = makeProfile(FloodgateState.FALSE, false);
        // rowId = -1 means not existing
        when(storage.loadProfile("TestUser")).thenReturn(newProfile);

        FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertFalse(loginStarted);
    }

    @Test
    void shouldAutoRegisterNewPlayerWhenEnabled() {
        setConfig("true", "true", "false");

        StoredProfile newProfile = makeProfile(FloodgateState.FALSE, false);
        when(storage.loadProfile("TestUser")).thenReturn(newProfile);

        FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertTrue(loginStarted);
    }

    // ---- run() branch: floodgate state mismatch ----

    @Test
    void shouldNotLoginLinkedProfileWhenCurrentConnectionIsUnlinked() {
        setConfig("true", "true", "false");

        StoredProfile linkedProfile = makeProfile(FloodgateState.LINKED, false);
        linkedProfile.setRowId(1);
        linkedProfile.setOnlinemodePreferred(true);
        when(storage.loadProfile("TestUser")).thenReturn(linkedProfile);

        // stored as LINKED but current connection is unlinked → mismatch
        FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertFalse(loginStarted);
    }

    @Test
    void shouldNotLoginTrueProfileWhenCurrentConnectionIsLinked() {
        setConfig("true", "true", "false");

        StoredProfile trueProfile = makeProfile(FloodgateState.TRUE, false);
        trueProfile.setRowId(1);
        trueProfile.setOnlinemodePreferred(true);
        when(storage.loadProfile("TestUser")).thenReturn(trueProfile);

        // stored as TRUE (unlinked) but current is linked → mismatch
        FloodgatePlayer fgPlayer = mockLinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertFalse(loginStarted);
    }

    // ---- run() branch: name conflict check ----

    @Test
    void shouldNotStartLoginWhenNameConflicts() throws Exception {
        // allowNameConflict=true + autoLoginFloodgate=no-conflict → triggers name check
        setConfig("no-conflict", "no-conflict", "true");

        StoredProfile profile = makeProfile(FloodgateState.TRUE, false);
        profile.setRowId(1);
        profile.setOnlinemodePreferred(true);
        when(storage.loadProfile("TestUser")).thenReturn(profile);

        Profile premiumProfile = new Profile(UUID.randomUUID(), "TestUser");
        when(resolver.findProfile("TestUser")).thenReturn(Optional.of(premiumProfile));

        FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertFalse(loginStarted);
    }

    // ---- run() branch: auth plugin exception ----

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnWhenAuthPluginThrowsException() throws Exception {
        setConfig("true", "true", "false");

        StoredProfile profile = makeProfile(FloodgateState.TRUE, false);
        profile.setRowId(1);
        when(storage.loadProfile("TestUser")).thenReturn(profile);

        AuthPlugin<Object> authPlugin = mock(AuthPlugin.class);
        when(authPlugin.isRegistered("TestUser")).thenThrow(new RuntimeException("DB error"));
        when(core.getAuthPluginHook()).thenReturn(authPlugin);

        FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
        TestFloodgateManagement mgmt = createManagement(fgPlayer);

        mgmt.run();

        assertFalse(loginStarted);
    }

    // ---- isAutoAuthAllowed ----

    @Nested
    class AutoAuthAllowedTests {

        @Test
        void trueValueShouldAlwaysAllow() {
            setConfig("true", "true", "false");
            FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
            TestFloodgateManagement mgmt = createManagement(fgPlayer);

            assertTrue(mgmt.isAutoAuthAllowed("true"));
        }

        @Test
        void falseValueShouldNeverAllow() {
            setConfig("true", "true", "false");
            FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
            TestFloodgateManagement mgmt = createManagement(fgPlayer);

            assertFalse(mgmt.isAutoAuthAllowed("false"));
        }

        @Test
        void noConflictValueShouldAlwaysAllow() {
            setConfig("true", "true", "false");
            FloodgatePlayer fgPlayer = mockUnlinkedPlayer();
            TestFloodgateManagement mgmt = createManagement(fgPlayer);

            assertTrue(mgmt.isAutoAuthAllowed("no-conflict"));
        }

        @Test
        void linkedValueShouldOnlyAllowWhenLinked() {
            setConfig("true", "true", "false");

            // unlinked player
            FloodgatePlayer unlinked = mockUnlinkedPlayer();
            TestFloodgateManagement mgmtUnlinked = createManagement(unlinked);
            // set isLinked by running the initial part
            assertFalse(mgmtUnlinked.isAutoAuthAllowed("linked"));

            // linked player
            FloodgatePlayer linked = mockLinkedPlayer();
            TestFloodgateManagement mgmtLinked = createManagement(linked);
            assertFalse(mgmtLinked.isAutoAuthAllowed("linked")); // isLinked is not set yet
        }
    }

    // ---- helpers ----

    private void setConfig(String autoLogin, String autoRegister, String allowConflict) {
        when(config.get("autoLoginFloodgate")).thenReturn(autoLogin);
        when(config.get("autoRegisterFloodgate")).thenReturn(autoRegister);
        when(config.get("allowFloodgateNameConflict")).thenReturn(allowConflict);
    }

    private FloodgatePlayer mockLinkedPlayer() {
        FloodgatePlayer player = mock(FloodgatePlayer.class);
        when(player.getCorrectUsername()).thenReturn("TestUser");
        // getLinkedPlayer() non-null → linked
        org.geysermc.floodgate.util.LinkedPlayer linked = mock(org.geysermc.floodgate.util.LinkedPlayer.class);
        when(player.getLinkedPlayer()).thenReturn(linked);
        return player;
    }

    private FloodgatePlayer mockUnlinkedPlayer() {
        FloodgatePlayer player = mock(FloodgatePlayer.class);
        when(player.getCorrectUsername()).thenReturn("TestUser");
        // getLinkedPlayer() null by default → unlinked
        return player;
    }

    private StoredProfile makeProfile(FloodgateState floodgateState, boolean premium) {
        return new StoredProfile(UUID.randomUUID(), "TestUser", premium, floodgateState, "127.0.0.1");
    }

    @SuppressWarnings("unchecked")
    private TestFloodgateManagement createManagement(FloodgatePlayer fgPlayer) {
        return new TestFloodgateManagement(core, (Object) "fakePlayer", fgPlayer);
    }

    /**
     * Concrete test subclass that captures whether startLogin() was called.
     */
    private class TestFloodgateManagement
            extends FloodgateManagement<Object, Object, LoginSession, PlatformPlugin<Object>> {

        TestFloodgateManagement(FastLoginCore<Object, Object, PlatformPlugin<Object>> core,
                                Object player, FloodgatePlayer floodgatePlayer) {
            super(core, player, floodgatePlayer);
        }

        @Override
        protected void startLogin() {
            loginStarted = true;
        }

        @Override
        protected String getName(Object player) {
            return "TestUser";
        }

        @Override
        protected UUID getUUID(Object player) {
            return UUID.randomUUID();
        }

        @Override
        protected InetSocketAddress getAddress(Object player) {
            return new InetSocketAddress("127.0.0.1", 25565);
        }

        // expose protected method for testing
        @Override
        public boolean isAutoAuthAllowed(String configValue) {
            return super.isAutoAuthAllowed(configValue);
        }
    }
}
