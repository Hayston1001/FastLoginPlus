/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.hooks.bedrock;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin.Registration;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth.Outcome.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class FloodgatePreLoginAuthTest {
    private static final String NAME = "Bedrock";
    private final FastLoginCore core = mock(FastLoginCore.class);
    private final PreLoginAuthPlugin auth = mock(PreLoginAuthPlugin.class);
    private final BedrockSession player = mock(BedrockSession.class);
    private final MojangResolver resolver = mock(MojangResolver.class);
    private final Configuration config = new Configuration();
    private final FloodgatePreLoginAuth handler = new FloodgatePreLoginAuth(core);
    private final Logger logger = mock(Logger.class);
    private final Registration registration = new Registration(NAME, "private hash", "private salt",
            UUID.randomUUID(), 1234L, null, 0L);

    @BeforeEach
    void setUp() throws Exception {
        PlatformPlugin plugin = mock(PlatformPlugin.class);
        doReturn(plugin).when(core).getPlugin();
        when(plugin.getLog()).thenReturn(logger);
        when(core.getConfig()).thenReturn(config);
        when(core.getResolver()).thenReturn(resolver);
        config.set("autoRegisterFloodgate", true);
        config.set("autoLoginFloodgate", true);
        config.set("allowFloodgateNameConflict", true);
        when(player.getAutoLogin()).thenAnswer(call -> config.get("autoLoginFloodgate").toString());
        when(player.getAutoRegister()).thenAnswer(call -> config.get("autoRegisterFloodgate").toString());
        when(player.getAllowConflict()).thenAnswer(call -> config.get("allowFloodgateNameConflict").toString());
        when(auth.isPreLoginForceLoginSupported()).thenReturn(true);
        when(auth.registerPlayer(eq(NAME), anyString())).thenReturn(true);
        when(auth.captureRegistration(NAME)).thenReturn(registration);
        when(auth.deleteRegistration(registration)).thenReturn(true);
        when(auth.forceLoginFromProxy(NAME)).thenReturn(true);
        when(resolver.findProfile(NAME)).thenReturn(Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({
            "false, true, false, REGISTERED_AND_LOGIN_REQUESTED",
            "false, false, false, NO_AUTO_REGISTER",
            "true, true, false, LOGIN_REQUESTED",
            "true, false, false, NO_AUTO_LOGIN",
            "false, linked, false, NO_AUTO_REGISTER",
            "false, linked, true, REGISTERED_AND_LOGIN_REQUESTED",
            "true, linked, false, NO_AUTO_LOGIN",
            "true, linked, true, LOGIN_REQUESTED",
            "false, no-conflict, false, REGISTERED_AND_LOGIN_REQUESTED",
            "true, no-conflict, false, LOGIN_REQUESTED"
    })
    void usesRegistrationPolicyAlongsideLoginPolicy(boolean registered, String option, boolean linked,
                                  FloodgatePreLoginAuth.Outcome expected) throws Exception {
        config.set("autoRegisterFloodgate", registered ? false : option);
        config.set("autoLoginFloodgate", registered ? option : true);
        when(auth.isRegistered(NAME)).thenReturn(registered);
        if (linked) {
            when(player.isLinked()).thenReturn(true);
        }
        assertEquals(expected, handler.handle(NAME, player, auth));
        if (registered || expected == NO_AUTO_REGISTER) {
            verify(auth, never()).registerPlayer(anyString(), anyString());
        }
    }

    @ParameterizedTest
    @CsvSource({"false, true", "linked, true", "true, no-conflict"})
    void conflictingUnlinkedNamesStayLocked(String conflict, String autoAuth) throws Exception {
        config.set("allowFloodgateNameConflict", conflict);
        config.set("autoRegisterFloodgate", autoAuth);
        when(resolver.findProfile(NAME)).thenReturn(Optional.of(new Profile(UUID.randomUUID(), NAME)));
        assertEquals(NAME_CONFLICT, handler.handle(NAME, player, auth));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void lookupFailureKeepsPasswordLock() throws Exception {
        config.set("allowFloodgateNameConflict", false);
        when(resolver.findProfile(NAME)).thenThrow(new IOException("offline"));
        assertEquals(NAME_CONFLICT, handler.handle(NAME, player, auth));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void noConflictLoginPolicyAlsoAppliesBeforeRegistration() throws Exception {
        config.set("autoLoginFloodgate", "no-conflict");
        when(resolver.findProfile(NAME)).thenReturn(Optional.of(new Profile(UUID.randomUUID(), NAME)));
        assertEquals(NAME_CONFLICT, handler.handle(NAME, player, auth));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void accountReadFailureKeepsPasswordLock() throws Exception {
        when(auth.isRegistered(NAME)).thenThrow(new IOException("database unavailable"));
        assertEquals(ACCOUNT_READ_FAILED, handler.handle(NAME, player, auth));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void newAccountCannotBeCreatedWhenAutoLoginIsDisabled() throws Exception {
        config.set("autoLoginFloodgate", false);
        assertEquals(NO_AUTO_LOGIN, handler.handle(NAME, player, auth));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void failedNewLoginDeletesOnlyTheSnapshotCapturedBeforeForceLogin() throws Exception {
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(REGISTERED_LOGIN_FAILED, handler.handle(NAME, player, auth));
        org.mockito.InOrder order = inOrder(auth);
        order.verify(auth).registerPlayer(eq(NAME), anyString());
        order.verify(auth).captureRegistration(NAME);
        order.verify(auth).forceLoginFromProxy(NAME);
        order.verify(auth).deleteRegistration(registration);
        verify(logger).warn(contains("synchronously deleted and read back as absent"), eq(NAME));
        verify(auth).isPreLoginForceLoginSupported();
        verify(auth).isRegistered(NAME);
        verifyNoMoreInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void linkedJavaNameIsExemptEvenWhenConflictsAreDisallowed(boolean registered) throws Exception {
        config.set("allowFloodgateNameConflict", false);
        when(auth.isRegistered(NAME)).thenReturn(registered);
        when(player.isLinked()).thenReturn(true);
        when(resolver.findProfile(NAME)).thenReturn(Optional.of(new Profile(UUID.randomUUID(), NAME)));

        assertEquals(registered ? LOGIN_REQUESTED : REGISTERED_AND_LOGIN_REQUESTED,
                handler.handle(NAME, player, auth));
        verify(resolver, never()).findProfile(anyString());
        verify(auth).forceLoginFromProxy(NAME);
    }

    @Test
    void linkedOnlyLoginPolicyAlsoBlocksUnlinkedRegistration() {
        config.set("autoLoginFloodgate", "linked");
        assertEquals(NO_AUTO_LOGIN, handler.handle(NAME, player, auth));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).forceLoginFromProxy(anyString());
    }

    @Test
    void failedDeletionRequiresOperatorRepair() {
        when(auth.deleteRegistration(registration)).thenReturn(false);
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handler.handle(NAME, player, auth));
        verify(auth).deleteRegistration(registration);
        verify(logger).error(contains("Operator repair is required"), eq(NAME));
    }

    @Test
    void absentSnapshotNeverAuthorizesDeletion() {
        when(auth.captureRegistration(NAME)).thenReturn(null);
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handler.handle(NAME, player, auth));
        verify(auth, never()).deleteRegistration(any());
        verify(logger).error(contains("Operator repair is required"), eq(NAME));
    }

    @Test
    void wrongNameSnapshotNeverAuthorizesDeletion() {
        when(auth.captureRegistration(NAME)).thenReturn(new Registration("Another", "hash", null,
                null, 1234L, null, 0L));
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handler.handle(NAME, player, auth));
        verify(auth, never()).deleteRegistration(any());
    }

    @Test
    void captureExceptionKeepsTheRowAndReportsOnlyTheErrorType() {
        when(auth.captureRegistration(NAME)).thenThrow(new IllegalStateException("private credential"));
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handler.handle(NAME, player, auth));
        verify(auth, never()).deleteRegistration(any());
        verify(logger).error("Backend Floodgate registration snapshot failed for {} (errorType={})",
                NAME, "IllegalStateException");
    }

    @Test
    void deletionExceptionIsNotCleanupSuccessAndDoesNotLeakSecrets() {
        when(auth.deleteRegistration(registration)).thenThrow(new IllegalStateException("private credential"));
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handler.handle(NAME, player, auth));
        verify(logger).error("Backend Floodgate account cleanup failed for {} (errorType={})",
                NAME, "IllegalStateException");
        verify(logger).error(contains("Operator repair is required"), eq(NAME));
    }

    @Test
    void successfulLoginCapturesImmediatelyButNeverDeletes() {
        assertEquals(REGISTERED_AND_LOGIN_REQUESTED, handler.handle(NAME, player, auth));
        org.mockito.InOrder order = inOrder(auth);
        order.verify(auth).registerPlayer(eq(NAME), anyString());
        order.verify(auth).captureRegistration(NAME);
        order.verify(auth).forceLoginFromProxy(NAME);
        verify(auth, never()).deleteRegistration(any());
    }

    @Test
    void existingAccountIsNeverCleanedUpOnFailedLogin() throws Exception {
        when(auth.isRegistered(NAME)).thenReturn(true);
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(FORCE_LOGIN_FAILED, handler.handle(NAME, player, auth));
        verify(auth, never()).registerPlayer(anyString(), anyString());
        verify(auth, never()).captureRegistration(anyString());
        verify(auth, never()).deleteRegistration(any());
        verify(auth).isPreLoginForceLoginSupported();
        verify(auth).isRegistered(NAME);
        verify(auth).forceLoginFromProxy(NAME);
        verifyNoMoreInteractions(auth);
    }

    @Test
    void falseInsertDoesNotGivePermissionToDeleteAConcurrentAccount() throws Exception {
        when(auth.isRegistered(NAME)).thenReturn(false, true);
        when(auth.registerPlayer(eq(NAME), anyString())).thenReturn(false);
        when(auth.forceLoginFromProxy(NAME)).thenReturn(false);
        assertEquals(FORCE_LOGIN_FAILED, handler.handle(NAME, player, auth));
        verify(auth).isPreLoginForceLoginSupported();
        verify(auth, times(2)).isRegistered(NAME);
        verify(auth).registerPlayer(eq(NAME), anyString());
        verify(auth).forceLoginFromProxy(NAME);
        verify(auth, never()).captureRegistration(anyString());
        verify(auth, never()).deleteRegistration(any());
        verifyNoMoreInteractions(auth);
    }
}
