/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import fr.xephi.authme.api.v3.AuthMeApi;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.doThrow;

class AuthMePreJoinTest {
    public interface PreJoinApi {
        void forceLoginFromProxy(String name);
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
        AuthMeHook hook = createHook(mock(AuthMeApi.class));
        assertFalse(hook.isPreLoginForceLoginSupported());
        assertFalse(hook.forceLoginFromProxy(".Bedrock"));
    }

    @Test
    void cleanupUsesAuthMesNameBasedApi() {
        AuthMeApi api = mock(AuthMeApi.class);
        assertTrue(createHook(api).unregisterPlayer(".Bedrock"));
        verify(api).forceUnregister(".Bedrock");
    }

    @Test
    void failedCleanupApiDoesNotReportSuccess() {
        AuthMeApi api = mock(AuthMeApi.class);
        doThrow(new UnsupportedOperationException("private credential"))
                .when(api).forceUnregister(".Bedrock");
        assertFalse(createHook(api).unregisterPlayer(".Bedrock"));
        verify(api).forceUnregister(".Bedrock");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private AuthMeHook createHook(AuthMeApi api) {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(core.getConfig()).thenReturn(new Configuration());
        try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
            singleton.when(AuthMeApi::getInstance).thenReturn(api);
            return new AuthMeHook(plugin);
        }
    }
}
