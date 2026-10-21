/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.model.skin.Textures;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.SkinsRestorerCompat;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class PaperCacheListenerTest {
    private static final String NAME = "TestUser";
    private final UUID connectionUuid = UUID.randomUUID();
    private final FastLoginBukkit plugin = mock(FastLoginBukkit.class);
    private final FastLoginCore core = mock(FastLoginCore.class);
    private final FileConfiguration config = mock(FileConfiguration.class);
    private final PlayerProfile profile = mock(PlayerProfile.class);
    private final AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
    private final PaperCacheListener listener = new PaperCacheListener(plugin);

    @BeforeEach
    void setUp() {
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getConfig()).thenReturn(config);
        when(event.getName()).thenReturn(NAME);
        when(event.getUniqueId()).thenReturn(connectionUuid);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(event.getLoginResult()).thenReturn(Result.ALLOWED);
    }

    @Test
    void deniesBeforeAdmissionOrSkinHandlingUntilServerIsStarted() {
        when(plugin.isServerFullyStarted()).thenReturn(false);
        when(core.getMessage("not-started")).thenReturn("Server is still starting");

        listener.onAsyncPlayerPreLogin(event);

        verify(event).disallow(Result.KICK_OTHER, "Server is still starting");
        verify(plugin).recordPreLoginAttestation(NAME, profile);
        verify(plugin, never()).isUntrustedPremiumConnection(any(), anyString(), any());
        verify(plugin, never()).getBungeeManager();
        verify(plugin, never()).getConfig();
        verify(profile, never()).setProperty(any());
    }

    @Test
    void preservesAnotherPluginsDenialDuringStartup() {
        when(event.getLoginResult()).thenReturn(Result.KICK_WHITELIST);

        listener.onAsyncPlayerPreLogin(event);

        verify(event, never()).disallow(any(Result.class), anyString());
        verify(plugin, never()).isServerFullyStarted();
        verify(plugin, never()).isUntrustedPremiumConnection(any(), anyString(), any());
    }

    @Test
    void readyServerStillEnforcesAdmissionBeforeSkinHandling() {
        when(plugin.isServerFullyStarted()).thenReturn(true);
        when(plugin.isUntrustedPremiumConnection(profile, NAME, connectionUuid)).thenReturn(true);
        when(core.getMessage("proxy-claim-required")).thenReturn("Claim required");

        listener.onAsyncPlayerPreLogin(event);

        verify(event).disallow(Result.KICK_OTHER, "Claim required");
        verify(core, never()).getMessage("not-started");
        verify(plugin, never()).getConfig();
        verify(profile, never()).setProperty(any());
    }

    @Test
    void readyServerAdmitsWithoutSkinForwardingWhenDisabled() {
        when(plugin.isServerFullyStarted()).thenReturn(true);

        listener.onAsyncPlayerPreLogin(event);

        verify(plugin).isUntrustedPremiumConnection(profile, NAME, connectionUuid);
        verify(event, never()).disallow(any(Result.class), anyString());
        verify(plugin, never()).getLoginSessions();
        verify(profile, never()).setProperty(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readyServerForwardsSkinWithSkinsRestorerTakingPrecedence(boolean customSkin) {
        when(plugin.isServerFullyStarted()).thenReturn(true);
        when(config.getBoolean("forwardSkin")).thenReturn(true);
        BukkitLoginSession session = new BukkitLoginSession(NAME, true);
        UUID premiumUuid = UUID.randomUUID();
        session.setUuid(premiumUuid);
        session.setSkinProperty(new SkinProperty("premium-value", "premium-signature"));
        ConcurrentHashMap<String, BukkitLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put("connection", session);
        when(plugin.getLoginSessions()).thenReturn(sessions);
        SkinsRestorerCompat skinsRestorer = mock(SkinsRestorerCompat.class);
        when(plugin.getSkinsRestorerCompat()).thenReturn(skinsRestorer);
        if (customSkin) {
            when(skinsRestorer.getCustomSkin(premiumUuid, connectionUuid))
                    .thenReturn(new SkinProperty("custom-value", "custom-signature"));
        }

        listener.onAsyncPlayerPreLogin(event);

        verify(plugin).isUntrustedPremiumConnection(profile, NAME, connectionUuid);
        verify(skinsRestorer).getCustomSkin(premiumUuid, connectionUuid);
        verify(profile).setProperty(new ProfileProperty(Textures.KEY,
                customSkin ? "custom-value" : "premium-value",
                customSkin ? "custom-signature" : "premium-signature"));
        verify(event, never()).disallow(any(Result.class), anyString());
        verify(core, never()).getMessage("not-started");
    }
}
