/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

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
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        doReturn(plugin).when(core).getPlugin();
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(plugin.getProxyId()).thenReturn(UUID.randomUUID());
        Configuration config = new Configuration();
        config.set("autoLogin", enabled);
        when(core.getConfig()).thenReturn(config);
        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn("TestUser");
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        VelocityLoginSession session = new VelocityLoginSession("TestUser", registered,
                new StoredProfile(id, "TestUser", true, FloodgateState.FALSE, "127.0.0.1"));
        session.setUuid(id);
        session.setIdentityDecision(IdentityClaim.Decision.PREMIUM_VERIFIED);
        RegisteredServer server = mock(RegisteredServer.class);
        new ForceLoginTask(core, player, server, session).onForceActionSuccess(session);
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
