/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.games647.fastlogin.bukkit.listener.PaperCacheListener;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class ProxyAdmissionTest {
    private static final String NAME = "TestUser";
    private static final String KICK = "proxy-claim-required";
    private final UUID uuid = UUID.randomUUID();
    private final UUID offlineUuid = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private final UUID proxy = UUID.randomUUID();
    private FastLoginBukkit plugin;
    private FastLoginCore core;
    private BungeeManager manager;
    private Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(FastLoginBukkit.class, CALLS_REAL_METHODS);
        core = mock(FastLoginCore.class);
        manager = mock(BungeeManager.class);
        logger = mock(Logger.class);
        set("core", core);
        set("bungeeManager", manager);
        set("proxyBedrockPreLogin", new ProxyBedrockPreLogin(plugin));
        set("logger", logger);
        when(manager.isEnabled()).thenReturn(true);
        when(manager.isProxyAllowed(proxy)).thenReturn(true);
        when(manager.getAllowedProxyIds()).thenReturn(java.util.Collections.singleton(proxy));
        when(core.getMessage(KICK)).thenReturn(KICK);
        doReturn(mock(FileConfiguration.class)).when(plugin).getConfig();
        doReturn(true).when(plugin).isServerFullyStarted();
        doNothing().when(plugin).recordPreLoginAttestation(anyString(), any());
    }

    @Test
    void pureDecisionMatrix() {
        assertFalse(FastLoginBukkit.isUntrustedPremiumConnection(false, false, uuid, false));
        assertFalse(FastLoginBukkit.isUntrustedPremiumConnection(true, true, uuid, false));
        assertTrue(FastLoginBukkit.isUntrustedPremiumConnection(true, false, uuid, false));
        assertFalse(FastLoginBukkit.isUntrustedPremiumConnection(true, false, uuid, true));
        assertFalse(FastLoginBukkit.isUntrustedPremiumConnection(true, false, offlineUuid, false));
    }

    @Test
    void rejectsAbsentMalformedDuplicateOrMismatchedClaimsAndUntrustedTokens() {
        String valid = claim(NAME, uuid, proxy);
        assertRejected(profile());
        assertRejected(profile("malformed"));
        assertRejected(profile(valid, claim(NAME, uuid, UUID.randomUUID())));
        assertRejected(profile(claim("OtherUser", uuid, proxy)));
        assertRejected(profile(claim(NAME, UUID.randomUUID(), proxy)));
        assertRejected(profile(claim(NAME, uuid, null)));
        assertRejected(profile(claim(NAME, uuid, UUID.randomUUID())));
        assertRejected(new Object());
        verify(logger, times(8)).warn(contains("Refusing login"), eq(NAME), eq(uuid), eq(1));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void rejectsClaimsThatDoNotProveTheV4Identity() {
        assertRejected(signedProfile(claim(IdentityClaim.Decision.OFFLINE, NAME, uuid, null, null), proxy));
        assertRejected(signedProfile(claim(IdentityClaim.Decision.PREMIUM_VERIFIED, NAME, uuid,
                UUID.randomUUID(), null), proxy));
        verify(logger, times(2)).warn(contains("Refusing login"), eq(NAME), eq(uuid), eq(1));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void acceptsOneAllowlistedClaimBoundToTheConnection() {
        assertFalse(plugin.isUntrustedPremiumConnection(signedProfile(claim(NAME, uuid, null), proxy), NAME, uuid));
        verifyNoInteractions(logger);
    }

    @Test
    void missingInvalidDuplicateAndUnknownSignaturesCannotAdmitV4() {
        String value = claim(NAME, uuid, null);
        assertRejected(profile(value));
        assertRejected(withSignatures(value, "malformed"));
        assertRejected(signedProfile(value, UUID.randomUUID()));
        assertRejected(withSignatures(value, IdentityClaim.sign(value, proxy), IdentityClaim.sign(value, proxy)));
        assertRejected(withSignatures(value, IdentityClaim.sign(value + " ", proxy)));
        when(manager.getAllowedProxyIds()).thenReturn(java.util.Collections.emptySet());
        assertRejected(signedProfile(value, proxy));
        assertRejected(profile(claim(NAME, uuid, proxy))); // Old plaintext-ID claim is not authentication.
    }

    @Test
    void anyAllowedProxyCanSignAndSignedVelocityNeedsNoExtraSignature() throws Exception {
        UUID second = UUID.randomUUID();
        when(manager.getAllowedProxyIds()).thenReturn(new LinkedHashSet<>(Arrays.asList(proxy, second)));
        String value = claim(NAME, uuid, null);
        Object profile = signedProfile(value, second);
        assertFalse(plugin.isUntrustedPremiumConnection(profile, NAME, uuid));
        java.lang.reflect.Method read = FastLoginBukkit.class.getDeclaredMethod("readIdentityDeclaration",
                Object.class, String.class, UUID.class);
        read.setAccessible(true);
        assertNotNull(read.invoke(plugin, profile, NAME, uuid));
        when(manager.isSignedForwarding()).thenReturn(true);
        when(manager.getAllowedProxyIds()).thenReturn(java.util.Collections.emptySet());
        assertNotNull(read.invoke(plugin, profile(value), NAME, uuid));
        verifyNoInteractions(logger);
    }

    @Test
    void exemptConnectionsNeedNoProfileOrClaim() {
        assertFalse(plugin.isUntrustedPremiumConnection(null, NAME, offlineUuid));
        when(manager.isSignedForwarding()).thenReturn(true);
        assertFalse(plugin.isUntrustedPremiumConnection(null, NAME, uuid));
        when(manager.isEnabled()).thenReturn(false);
        when(manager.isSignedForwarding()).thenReturn(false);
        assertFalse(plugin.isUntrustedPremiumConnection(null, NAME, uuid));
        verifyNoInteractions(logger);
    }

    @Test
    void paperPreLoginDeniesBeforeAnyLaterPremiumHandling() {
        AsyncPlayerPreLoginEvent event = event(profile());
        new PaperCacheListener(plugin).onAsyncPlayerPreLogin(event);
        verify(event).disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, KICK);
        verify(logger).warn(argThat((String message) -> message.contains(
                "no signature matched any of the {} loaded proxy IDs")
                && message.contains("Restart the backend if allowed-proxies.txt changed.")), eq(NAME), eq(uuid), eq(1));
        verifyNoMoreInteractions(logger);
        verify(core, never()).getAuthPluginHook();
    }

    @Test
    void paperPreLoginAdmitsValidClaimAndOfflineConnection() {
        AsyncPlayerPreLoginEvent valid = event(signedProfile(claim(NAME, uuid, null), proxy));
        new PaperCacheListener(plugin).onAsyncPlayerPreLogin(valid);
        verify(valid, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), anyString());
        AsyncPlayerPreLoginEvent offline = event(profile());
        when(offline.getUniqueId()).thenReturn(offlineUuid);
        new PaperCacheListener(plugin).onAsyncPlayerPreLogin(offline);
        verify(offline, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), anyString());
        verifyNoInteractions(logger);
    }

    @Test
    void paperSkipsAdmissionWhenAnotherPluginAlreadyDeniedLogin() {
        AsyncPlayerPreLoginEvent event = event(profile());
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        new PaperCacheListener(plugin).onAsyncPlayerPreLogin(event);
        verify(plugin, never()).isUntrustedPremiumConnection(any(), anyString(), any());
        verify(event, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), anyString());
        verifyNoInteractions(logger);
    }

    private void assertRejected(Object profile) {
        assertTrue(plugin.isUntrustedPremiumConnection(profile, NAME, uuid));
    }

    private String claim(String name, UUID forwarded, UUID source) {
        return claim(IdentityClaim.Decision.PREMIUM_VERIFIED, name, forwarded, uuid, source);
    }

    private String claim(IdentityClaim.Decision decision, String name, UUID forwarded, UUID verified,
                         UUID source) {
        return new IdentityClaim(decision, name, forwarded, verified,
                IdentityClaim.Facts.defaults(), source).encode();
    }

    private PlayerProfile profile(String... claims) {
        PlayerProfile profile = mock(PlayerProfile.class);
        LinkedHashSet<ProfileProperty> properties = new LinkedHashSet<>();
        Arrays.stream(claims).forEach(value -> properties.add(
                new ProfileProperty(IdentityClaim.PROPERTY_NAME, value)));
        when(profile.getProperties()).thenReturn(properties);
        return profile;
    }

    private PlayerProfile signedProfile(String claim, UUID signer) {
        return withSignatures(claim, IdentityClaim.sign(claim, signer));
    }

    private PlayerProfile withSignatures(String claim, String... signatures) {
        PlayerProfile profile = profile(claim);
        LinkedHashSet<ProfileProperty> properties = new LinkedHashSet<>(profile.getProperties());
        // Use distinct mocks so identical duplicate values remain visible to validation.
        for (String signature : signatures) {
            ProfileProperty property = mock(ProfileProperty.class);
            when(property.getName()).thenReturn(IdentityClaim.SIGNATURE_PROPERTY_NAME);
            when(property.getValue()).thenReturn(signature);
            properties.add(property);
        }
        when(profile.getProperties()).thenReturn(properties);
        return profile;
    }

    private AsyncPlayerPreLoginEvent event(PlayerProfile profile) {
        AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
        when(event.getName()).thenReturn(NAME);
        when(event.getUniqueId()).thenReturn(uuid);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        return event;
    }

    private void set(String name, Object value) throws Exception {
        Field field = FastLoginBukkit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
