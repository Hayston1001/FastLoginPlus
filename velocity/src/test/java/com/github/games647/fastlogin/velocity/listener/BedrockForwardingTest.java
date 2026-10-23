/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.protocol.BedrockClaim;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import net.md_5.bungee.config.Configuration;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class BedrockForwardingTest {
    @Test
    void trustedFloodgateFactsAreRefreshedPerBackendAttemptWithoutChangingIdentity() {
        UUID proxy = UUID.randomUUID();
        UUID uuid = UUID.randomUUID();
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        FastLoginCore core = mock(FastLoginCore.class);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(plugin.getProxyId()).thenReturn(proxy);
        Configuration config = new Configuration();
        config.set("autoLoginFloodgate", true);
        config.set("autoRegisterFloodgate", "linked");
        config.set("allowFloodgateNameConflict", false);
        when(core.getConfig()).thenReturn(config);
        FloodgateService floodgate = mock(FloodgateService.class);
        when(plugin.getFloodgateService()).thenReturn(floodgate);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("Bedrock");
        IdentityClaim identity = new IdentityClaim(IdentityClaim.Decision.UNDECIDED, "Bedrock", uuid, null);
        when(player.getGameProfileProperties()).thenReturn(Collections.singletonList(
                new GameProfile.Property(IdentityClaim.PROPERTY_NAME, identity.encode(), "")));
        ServerPreConnectEvent event = mock(ServerPreConnectEvent.class);
        when(event.getPlayer()).thenReturn(player);
        ConnectListener listener = new ConnectListener(plugin, null);
        listener.refreshBedrockClaim(event);
        verify(player, never()).setGameProfileProperties(anyList());
        FloodgatePlayer bedrock = mock(FloodgatePlayer.class);
        when(bedrock.getLinkedPlayer()).thenAnswer(call -> mock(call.getMethod().getReturnType()));
        when(floodgate.getBedrockPlayer(uuid)).thenReturn(bedrock);
        listener.refreshBedrockClaim(event);
        listener.refreshBedrockClaim(event);
        ArgumentCaptor<List<GameProfile.Property>> properties = ArgumentCaptor.forClass(List.class);
        verify(player, times(2)).setGameProfileProperties(properties.capture());
        BedrockClaim previous = null;
        for (List<GameProfile.Property> sent : properties.getAllValues()) {
            assertEquals(identity.encode(), sent.get(0).getValue());
            assertEquals(BedrockClaim.PROPERTY_NAME, sent.get(1).getName());
            BedrockClaim claim = BedrockClaim.verify(sent.get(1).getValue(), "Bedrock", uuid,
                    Collections.singleton(proxy), System.currentTimeMillis());
            assertTrue(claim.isLinked());
            assertEquals("true", claim.getAutoLogin());
            assertEquals("linked", claim.getAutoRegister());
            assertEquals("false", claim.getAllowConflict());
            if (previous != null) {
                assertNotEquals(previous.getNonce(), claim.getNonce());
            }
            previous = claim;
        }
    }
}
