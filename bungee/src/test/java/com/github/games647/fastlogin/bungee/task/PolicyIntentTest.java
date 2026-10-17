/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.task;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class PolicyIntentTest {
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void disabledPolicyNeverSendsLegacyForceLoginOrRegister(boolean enabled, boolean registered) {
        FastLoginCore core = mock(FastLoginCore.class);
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        doReturn(plugin).when(core).getPlugin();
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        Configuration config = new Configuration();
        config.set("autoLogin", enabled);
        when(core.getConfig()).thenReturn(config);
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("TestUser");
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        BungeeLoginSession session = new BungeeLoginSession("TestUser", registered,
                new StoredProfile(id, "TestUser", true, FloodgateState.FALSE, "127.0.0.1"));
        session.setUuid(id);
        session.setIdentityDecision(IdentityClaim.Decision.PREMIUM_VERIFIED);
        Server server = mock(Server.class);
        ProxyServer proxy = mock(ProxyServer.class, RETURNS_DEEP_STUBS);
        when(proxy.getConfig().getUuid()).thenReturn(UUID.randomUUID().toString());
        try (MockedStatic<ProxyServer> global = mockStatic(ProxyServer.class)) {
            global.when(ProxyServer::getInstance).thenReturn(proxy);
            new ForceLoginTask(core, player, server, session).onForceActionSuccess(session);
        }
        assertEquals(enabled, session.getFacts().autoLogin());
        assertTrue(session.getFacts().policyDeclared());
        assertEquals(IdentityClaim.Decision.PREMIUM_VERIFIED, session.getIdentityDecision());
        if (enabled) {
            ArgumentCaptor<LoginActionMessage> sent = ArgumentCaptor.forClass(LoginActionMessage.class);
            verify(plugin).sendPluginMessage(eq(server), sent.capture());
            assertEquals(registered ? LoginActionMessage.Type.LOGIN : LoginActionMessage.Type.REGISTER,
                    sent.getValue().getType());
            assertEquals(id, sent.getValue().getVerifiedPremiumUuid());
        } else {
            verify(plugin, never()).sendPluginMessage(any(), any());
        }
    }
}
