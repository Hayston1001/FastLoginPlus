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
        session.setConnectionUuid(connectionUuid);
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

    @Test
    void matchesSkinsByConnectionUuidWhenNamesCollide() {
        when(plugin.isServerFullyStarted()).thenReturn(true);
        when(config.getBoolean("forwardSkin")).thenReturn(true);
        when(plugin.getSkinsRestorerCompat()).thenReturn(mock(SkinsRestorerCompat.class));

        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        PlayerProfile firstProfile = mock(PlayerProfile.class);
        PlayerProfile secondProfile = mock(PlayerProfile.class);
        AsyncPlayerPreLoginEvent firstEvent = preLogin(firstUuid, firstProfile);
        AsyncPlayerPreLoginEvent secondEvent = preLogin(secondUuid, secondProfile);

        ConcurrentHashMap<String, BukkitLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put("first", session(firstUuid, "first-value", "first-signature"));
        sessions.put("second", session(secondUuid, "second-value", "second-signature"));
        when(plugin.getLoginSessions()).thenReturn(sessions);

        // interleaved lookups: each connection must read its own session, not the first same-name one
        listener.onAsyncPlayerPreLogin(firstEvent);
        listener.onAsyncPlayerPreLogin(secondEvent);
        listener.onAsyncPlayerPreLogin(firstEvent);

        verify(firstProfile, times(2)).setProperty(new ProfileProperty(Textures.KEY,
                "first-value", "first-signature"));
        verify(secondProfile).setProperty(new ProfileProperty(Textures.KEY,
                "second-value", "second-signature"));
    }

    @Test
    void doesNotBorrowAnotherConnectionsSkinForAnUnknownConnection() {
        when(plugin.isServerFullyStarted()).thenReturn(true);
        when(config.getBoolean("forwardSkin")).thenReturn(true);

        PlayerProfile eventProfile = mock(PlayerProfile.class);
        AsyncPlayerPreLoginEvent loginEvent = preLogin(UUID.randomUUID(), eventProfile);
        ConcurrentHashMap<String, BukkitLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put("known", session(UUID.randomUUID(), "known-value", "known-signature"));
        when(plugin.getLoginSessions()).thenReturn(sessions);

        listener.onAsyncPlayerPreLogin(loginEvent);

        verify(eventProfile, never()).setProperty(any());
        verify(plugin, never()).getSkinsRestorerCompat();
    }

    @Test
    void refusesSkinWhenTwoDifferentIdentitiesShareNameAndConnectionUuid() {
        when(plugin.isServerFullyStarted()).thenReturn(true);
        when(config.getBoolean("forwardSkin")).thenReturn(true);

        UUID sharedUuid = UUID.randomUUID();
        BukkitLoginSession verified = session(sharedUuid, "premium-value", "premium-signature");
        verified.setUuid(UUID.randomUUID());
        verified.setVerifiedPremium(true);
        BukkitLoginSession cracked = new BukkitLoginSession(NAME, false);
        cracked.setConnectionUuid(sharedUuid);

        ConcurrentHashMap<String, BukkitLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put("verified", verified);
        sessions.put("cracked", cracked);
        when(plugin.getLoginSessions()).thenReturn(sessions);

        PlayerProfile eventProfile = mock(PlayerProfile.class);
        listener.onAsyncPlayerPreLogin(preLogin(sharedUuid, eventProfile));

        // the same name and UUID cannot be tied to one identity: fail closed instead of guessing
        verify(eventProfile, never()).setProperty(any());
    }

    @Test
    void reusesACachedSessionOfTheSameVerifiedIdentity() {
        when(plugin.isServerFullyStarted()).thenReturn(true);
        when(config.getBoolean("forwardSkin")).thenReturn(true);
        when(plugin.getSkinsRestorerCompat()).thenReturn(mock(SkinsRestorerCompat.class));

        UUID sharedUuid = UUID.randomUUID();
        UUID premiumUuid = UUID.randomUUID();
        BukkitLoginSession previous = session(sharedUuid, "premium-value", "premium-signature");
        previous.setUuid(premiumUuid);
        previous.setVerifiedPremium(true);
        BukkitLoginSession current = new BukkitLoginSession(NAME, true);
        current.setConnectionUuid(sharedUuid);
        current.setUuid(premiumUuid);
        current.setVerifiedPremium(true);

        ConcurrentHashMap<String, BukkitLoginSession> sessions = new ConcurrentHashMap<>();
        sessions.put("previous", previous);
        sessions.put("current", current);
        when(plugin.getLoginSessions()).thenReturn(sessions);

        listener.onAsyncPlayerPreLogin(preLogin(sharedUuid, profile));

        // a still-cached previous session of the same verified account may supply the skin
        verify(profile).setProperty(new ProfileProperty(Textures.KEY,
                "premium-value", "premium-signature"));
    }

    private static AsyncPlayerPreLoginEvent preLogin(UUID uuid, PlayerProfile eventProfile) {
        AsyncPlayerPreLoginEvent loginEvent = mock(AsyncPlayerPreLoginEvent.class);
        when(loginEvent.getName()).thenReturn(NAME);
        when(loginEvent.getUniqueId()).thenReturn(uuid);
        when(loginEvent.getPlayerProfile()).thenReturn(eventProfile);
        when(loginEvent.getLoginResult()).thenReturn(Result.ALLOWED);
        return loginEvent;
    }

    private static BukkitLoginSession session(UUID uuid, String skinValue, String skinSignature) {
        BukkitLoginSession session = new BukkitLoginSession(NAME, true);
        session.setConnectionUuid(uuid);
        session.setSkinProperty(new SkinProperty(skinValue, skinSignature));
        return session;
    }
}
