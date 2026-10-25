/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin.Registration;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockSession;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import fr.xephi.authme.api.v3.AuthMeApi;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

import static com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class AuthMePreJoinTest {
    private static final String NAME = ".Bedrock";
    private final FastLoginBukkit plugin = mock(FastLoginBukkit.class);
    private final FastLoginCore core = mock(FastLoginCore.class);
    private final Logger logger = mock(Logger.class);
    private final AuthMePremiumIntegrator integrator = mock(AuthMePremiumIntegrator.class);
    private final Registration registration = new Registration(NAME, "private hash", "private salt",
            UUID.randomUUID(), 1234L, null, 0L);

    public interface PreJoinApi {
        void forceLoginFromProxy(String name);
    }

    @BeforeEach
    void setUp() {
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getLog()).thenReturn(logger);
        doReturn(plugin).when(core).getPlugin();
        when(core.getConfig()).thenReturn(new Configuration());
        when(plugin.getAuthMePremiumIntegrator()).thenReturn(integrator);
        when(integrator.captureRegistration(NAME)).thenReturn(registration);
        when(integrator.deleteRegistration(registration)).thenReturn(true);
    }

    @Test
    void newerApiUsesOfflineRegistrationAndNameBasedLogin() throws Exception {
        AuthMeApi api = mock(AuthMeApi.class, withSettings().extraInterfaces(PreJoinApi.class));
        when(api.registerPlayer(".Bedrock", "generated")).thenReturn(true);
        AuthMeHook hook = createHook(api);

        assertTrue(hook.isPreLoginForceLoginSupported());
        assertTrue(hook.registerPlayer(".Bedrock", "generated"));
        assertTrue(hook.forceLoginFromProxy(".Bedrock"));

        verify(api).registerPlayer(".Bedrock", "generated");
        verify((PreJoinApi) api).forceLoginFromProxy(".Bedrock");
    }

    @Test
    void olderApiLeavesBackendAssistanceDisabled() {
        AuthMeApi api = mock(AuthMeApi.class);
        AuthMeHook hook = createHook(api);
        assertFalse(hook.isPreLoginForceLoginSupported());
        assertFalse(hook.forceLoginFromProxy(".Bedrock"));
        assertEquals(UNSUPPORTED, handle(hook));
        verifyNoInteractions(api);
    }

    @Test
    void failedNewLoginDelegatesOnlyTheCapturedSnapshotWithoutAsyncUnregister() {
        AuthMeApi api = mock(AuthMeApi.class, withSettings().extraInterfaces(PreJoinApi.class));
        when(api.registerPlayer(eq(NAME), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("private credential")).when((PreJoinApi) api).forceLoginFromProxy(NAME);
        assertEquals(REGISTERED_LOGIN_FAILED, handle(createHook(api)));
        org.mockito.InOrder order = inOrder(api, integrator);
        order.verify(api).registerPlayer(eq(NAME), anyString());
        order.verify(integrator).captureRegistration(NAME);
        order.verify((PreJoinApi) api).forceLoginFromProxy(NAME);
        order.verify(integrator).deleteRegistration(registration);
        verify(api, never()).forceUnregister(anyString());
        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(api).registerPlayer(eq(NAME), password.capture());
        assertNoSecretLogs(password.getValue());
    }

    @Test
    void missingIntegratorRequiresRepairWithoutAsyncFallback() {
        when(plugin.getAuthMePremiumIntegrator()).thenReturn(null);
        AuthMeApi api = failingNewLogin();
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handle(createHook(api)));
        verify(api, never()).forceUnregister(anyString());
        verify(logger).error(contains("Operator repair is required"), eq(NAME));
        verifyNoInteractions(integrator);
    }

    @Test
    void missingSnapshotNeverDeletes() {
        when(integrator.captureRegistration(NAME)).thenReturn(null);
        AuthMeApi api = failingNewLogin();
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handle(createHook(api)));
        verify(integrator, never()).deleteRegistration(any());
        verify(api, never()).forceUnregister(anyString());
    }

    @Test
    void failedOrUnverifiedDeletionNeverReportsSuccess() {
        when(integrator.deleteRegistration(registration)).thenReturn(false);
        AuthMeApi api = failingNewLogin();
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handle(createHook(api)));
        verify(integrator).deleteRegistration(registration);
        verify(api, never()).forceUnregister(anyString());
        verify(logger).error(contains("Operator repair is required"), eq(NAME));
    }

    @Test
    void deletionExceptionNeverReportsSuccessOrLogsSecrets() {
        when(integrator.deleteRegistration(registration)).thenThrow(new IllegalStateException("private credential"));
        AuthMeApi api = failingNewLogin();
        assertEquals(REGISTERED_LOGIN_CLEANUP_FAILED, handle(createHook(api)));
        verify(api, never()).forceUnregister(anyString());
        assertNoSecretLogs();
    }

    @Test
    void existingAccountFailureNeverCallsAnyDeletionApi() {
        AuthMeApi api = mock(AuthMeApi.class, withSettings().extraInterfaces(PreJoinApi.class));
        when(api.isRegistered(NAME)).thenReturn(true);
        doThrow(new IllegalStateException("private credential")).when((PreJoinApi) api).forceLoginFromProxy(NAME);
        assertEquals(FORCE_LOGIN_FAILED, handle(createHook(api)));
        verify(api, never()).registerPlayer(anyString(), anyString());
        verify(api, never()).forceUnregister(anyString());
        verifyNoInteractions(integrator);
    }

    @Test
    void falseInsertRaceNeverCallsAnyDeletionApi() {
        AuthMeApi api = mock(AuthMeApi.class, withSettings().extraInterfaces(PreJoinApi.class));
        when(api.isRegistered(NAME)).thenReturn(false, true);
        when(api.registerPlayer(eq(NAME), anyString())).thenReturn(false);
        doThrow(new IllegalStateException("private credential")).when((PreJoinApi) api).forceLoginFromProxy(NAME);
        assertEquals(FORCE_LOGIN_FAILED, handle(createHook(api)));
        verify(api, never()).forceUnregister(anyString());
        verifyNoInteractions(integrator);
    }

    @Test
    void hooksAndPreJoinTestsRemainExactMirrors() throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("bukkit"))) { root = root.getParent(); }
        for (String file : Arrays.asList(
                "src/main/java/com/github/games647/fastlogin/bukkit/hook/AuthMeHook.java",
                "src/test/java/com/github/games647/fastlogin/bukkit/hook/AuthMePreJoinTest.java")) {
            assertEquals(new String(Files.readAllBytes(root.resolve("bukkit").resolve(file)), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(root.resolve("folia").resolve(file)), StandardCharsets.UTF_8));
        }
    }

    private FloodgatePreLoginAuth.Outcome handle(AuthMeHook hook) {
        return new FloodgatePreLoginAuth(core).handle(NAME, new BedrockSession(false, "true", "true", "true"), hook);
    }

    private AuthMeApi failingNewLogin() {
        AuthMeApi api = mock(AuthMeApi.class, withSettings().extraInterfaces(PreJoinApi.class));
        when(api.registerPlayer(eq(NAME), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("private credential")).when((PreJoinApi) api).forceLoginFromProxy(NAME);
        return api;
    }

    private void assertNoSecretLogs(String... additionalSecrets) {
        mockingDetails(logger).getInvocations().forEach(invocation -> {
            for (Object argument : invocation.getArguments()) {
                assertFalse(argument instanceof Throwable);
                assertFalse(String.valueOf(argument).contains("private credential"));
                assertFalse(String.valueOf(argument).contains("private hash"));
                assertFalse(String.valueOf(argument).contains("private salt"));
                assertFalse(argument instanceof Registration);
                for (String secret : additionalSecrets) { assertFalse(String.valueOf(argument).contains(secret)); }
            }
        });
    }

    private AuthMeHook createHook(AuthMeApi api) {
        try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
            singleton.when(AuthMeApi::getInstance).thenReturn(api);
            return new AuthMeHook(plugin);
        }
    }
}
