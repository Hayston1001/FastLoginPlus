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
package com.github.games647.fastlogin.core.hooks.bedrock;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.shared.LoginSource;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.config.Configuration;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
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

class FloodgateServiceTest {

    private FloodgateApi floodgateApi;
    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core;
    private FloodgateService service;
    private MojangResolver resolver;
    private Configuration config;
    private PlatformPlugin<Object> plugin;
    private LoginSource source;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        floodgateApi = mock(FloodgateApi.class);
        core = mock(FastLoginCore.class);
        resolver = mock(MojangResolver.class);
        config = mock(Configuration.class);
        plugin = mock(PlatformPlugin.class);
        source = mock(LoginSource.class);
        Logger logger = mock(Logger.class);

        when(core.getConfig()).thenReturn(config);
        doReturn(plugin).when(core).getPlugin();
        when(core.getResolver()).thenReturn(resolver);
        when(plugin.getLog()).thenReturn(logger);

        // default: allowFloodgateNameConflict = "false"
        when(config.get("allowFloodgateNameConflict")).thenReturn("false");

        service = new FloodgateService(floodgateApi, core);
    }

    // --- isValidFloodgateConfigString ---

    @Test
    void validConfigTrueShouldReturnTrue() {
        when(config.get("autoLoginFloodgate")).thenReturn("true");
        assertTrue(service.isValidFloodgateConfigString("autoLoginFloodgate"));
    }

    @Test
    void validConfigFalseShouldReturnTrue() {
        when(config.get("autoLoginFloodgate")).thenReturn("false");
        assertTrue(service.isValidFloodgateConfigString("autoLoginFloodgate"));
    }

    @Test
    void validConfigLinkedShouldReturnTrue() {
        when(config.get("autoLoginFloodgate")).thenReturn("linked");
        assertTrue(service.isValidFloodgateConfigString("autoLoginFloodgate"));
    }

    @Test
    void validConfigNoConflictShouldReturnTrue() {
        when(config.get("autoLoginFloodgate")).thenReturn("no-conflict");
        assertTrue(service.isValidFloodgateConfigString("autoLoginFloodgate"));
    }

    @Test
    void invalidConfigValueShouldReturnFalse() {
        when(config.get("autoLoginFloodgate")).thenReturn("maybe");
        assertFalse(service.isValidFloodgateConfigString("autoLoginFloodgate"));
    }

    // --- isUsernameForbidden ---

    @Test
    void usernameWithPrefixShouldBeForbidden() {
        when(floodgateApi.getPlayerPrefix()).thenReturn(".");
        assertTrue(service.isUsernameForbidden(makeProfile(".Steve")));
    }

    @Test
    void usernameWithoutPrefixShouldNotBeForbidden() {
        when(floodgateApi.getPlayerPrefix()).thenReturn(".");
        assertFalse(service.isUsernameForbidden(makeProfile("Steve")));
    }

    @Test
    void emptyPrefixShouldNotForbidAnyName() {
        when(floodgateApi.getPlayerPrefix()).thenReturn("");
        assertFalse(service.isUsernameForbidden(makeProfile(".Steve")));
    }

    // --- performChecks ---

    @Test
    void performChecksShouldReturnTrueForBedrockPlayer() {
        FloodgatePlayer fgPlayer = mockFloodgatePlayer("BedrockUser");
        when(floodgateApi.getPlayers()).thenReturn(Collections.singletonList(fgPlayer));

        assertTrue(service.performChecks("BedrockUser", source));
    }

    @Test
    void performChecksShouldKickOnNameConflictWhenNotAllowed() throws Exception {
        FloodgatePlayer fgPlayer = mockFloodgatePlayer("ConflictUser");
        when(floodgateApi.getPlayers()).thenReturn(Collections.singletonList(fgPlayer));

        Profile premiumProfile = new Profile(UUID.randomUUID(), "ConflictUser");
        when(resolver.findProfile("ConflictUser")).thenReturn(Optional.of(premiumProfile));

        boolean result = service.performChecks("ConflictUser", source);

        assertTrue(result);
        verify(source).kick(anyString());
    }

    @Test
    void performChecksShouldSkipConflictCheckWhenAllowed() throws Exception {
        when(config.get("allowFloodgateNameConflict")).thenReturn("true");
        service = new FloodgateService(floodgateApi, core);

        FloodgatePlayer fgPlayer = mockFloodgatePlayer("NoCheck");
        when(floodgateApi.getPlayers()).thenReturn(Collections.singletonList(fgPlayer));

        assertTrue(service.performChecks("NoCheck", source));
        // should NOT attempt to resolve profile since check was skipped
        verify(resolver, never()).findProfile("NoCheck");
    }

    @Test
    void performChecksShouldKickOnIoException() throws Exception {
        FloodgatePlayer fgPlayer = mockFloodgatePlayer("IoError");
        when(floodgateApi.getPlayers()).thenReturn(Collections.singletonList(fgPlayer));
        when(resolver.findProfile("IoError")).thenThrow(new IOException("network error"));

        service.performChecks("IoError", source);

        verify(source).kick(anyString());
    }

    @Test
    void performChecksShouldKickOnRateLimit() throws Exception {
        FloodgatePlayer fgPlayer = mockFloodgatePlayer("RateLimited");
        when(floodgateApi.getPlayers()).thenReturn(Collections.singletonList(fgPlayer));
        when(resolver.findProfile("RateLimited")).thenThrow(new RateLimitException());

        service.performChecks("RateLimited", source);

        verify(source).kick(anyString());
    }

    @Test
    void performChecksShouldNotKickWhenNameDoesNotConflict() throws Exception {
        FloodgatePlayer fgPlayer = mockFloodgatePlayer("UniqueName");
        when(floodgateApi.getPlayers()).thenReturn(Collections.singletonList(fgPlayer));
        when(resolver.findProfile("UniqueName")).thenReturn(Optional.empty());

        assertTrue(service.performChecks("UniqueName", source));
        verify(source, never()).kick(anyString());
    }

    // --- getBedrockPlayer ---

    @Test
    void getBedrockPlayerByNameShouldFindMatchingPlayer() {
        FloodgatePlayer alice = mockFloodgatePlayer("Alice");
        FloodgatePlayer bob = mockFloodgatePlayer("Bob");
        when(floodgateApi.getPlayers()).thenReturn(Arrays.asList(alice, bob));

        assertTrue(service.isBedrockConnection("Alice"));
        assertTrue(service.isBedrockConnection("Bob"));
        assertFalse(service.isBedrockConnection("Charlie"));
    }

    @Test
    void getBedrockPlayerByUuidShouldDelegate() {
        UUID uuid = UUID.randomUUID();
        FloodgatePlayer fgPlayer = mock(FloodgatePlayer.class);
        when(floodgateApi.getPlayer(uuid)).thenReturn(fgPlayer);

        assertTrue(service.isBedrockPlayer(uuid));
    }

    @Test
    void getBedrockPlayerByUuidShouldReturnFalseWhenNull() {
        UUID uuid = UUID.randomUUID();
        when(floodgateApi.getPlayer(uuid)).thenReturn(null);

        assertFalse(service.isBedrockPlayer(uuid));
    }

    // --- helpers ---

    private FloodgatePlayer mockFloodgatePlayer(String name) {
        FloodgatePlayer player = mock(FloodgatePlayer.class);
        when(player.getCorrectUsername()).thenReturn(name);
        // getLinkedPlayer() returns null by default (Mockito), which means unlinked
        return player;
    }

    private StoredProfile makeProfile(String name) {
        return new StoredProfile(UUID.randomUUID(), name, false, FloodgateState.FALSE, "127.0.0.1");
    }
}
