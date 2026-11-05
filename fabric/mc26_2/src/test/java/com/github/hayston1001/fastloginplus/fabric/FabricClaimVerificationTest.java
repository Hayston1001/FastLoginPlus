/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

class FabricClaimVerificationTest {

    private static final UUID PROXY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String NAME = "TestUser";
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Test
    void signedFreshClaimIsAccepted() {
        IdentityClaim claim = claim(System.currentTimeMillis());
        GameProfile profile = profile(claim, IdentityClaim.sign(claim.encode(), PROXY));

        try (var config = config()) {
            assertNotNull(FastLoginFabric.parseVerifiedClaim(profile));
        }
    }

    @Test
    void tamperedSignatureIsRejected() {
        IdentityClaim claim = claim(System.currentTimeMillis());
        GameProfile profile = profile(claim, IdentityClaim.sign(claim.encode() + " ", PROXY));

        try (var config = config()) {
            assertThrows(IllegalArgumentException.class,
                    () -> FastLoginFabric.parseVerifiedClaim(profile));
        }
    }

    @Test
    void staleClaimIsRejectedAtLoginButStillUsableForCommands() {
        IdentityClaim claim = claim(System.currentTimeMillis() - IdentityClaim.MAX_AGE_MILLIS - 5_000L);
        GameProfile profile = profile(claim, IdentityClaim.sign(claim.encode(), PROXY));

        try (var config = config()) {
            assertThrows(IllegalArgumentException.class,
                    () -> FastLoginFabric.parseVerifiedClaim(profile));
            // the same profile property stays on the session; an admin command
            // issued later must still be able to read it
            assertNotNull(FastLoginFabric.parseClaim(profile));
        }
    }

    @Test
    void unsignedClaimKeepsTheTransportTrust() {
        IdentityClaim claim = claim(System.currentTimeMillis());
        GameProfile profile = profile(claim, null);

        try (var config = config()) {
            assertNotNull(FastLoginFabric.parseVerifiedClaim(profile));
        }
    }

    private static org.mockito.MockedStatic<FabricConfig> config() {
        var config = mockStatic(FabricConfig.class);
        config.when(FabricConfig::startup).thenReturn(new FabricConfig.StartupSettings(PROXY.toString()));
        return config;
    }

    private static IdentityClaim claim(long issuedAt) {
        return new IdentityClaim(IdentityClaim.Decision.OFFLINE, NAME, OFFLINE, null,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.NO, true, false, false),
                null, issuedAt, UUID.randomUUID());
    }

    private static GameProfile profile(IdentityClaim claim, String signature) {
        var properties = ArrayListMultimap.<String, Property>create();
        properties.put(IdentityClaim.PROPERTY_NAME, new Property(IdentityClaim.PROPERTY_NAME, claim.encode()));
        if (signature != null) {
            properties.put(IdentityClaim.SIGNATURE_PROPERTY_NAME,
                    new Property(IdentityClaim.SIGNATURE_PROPERTY_NAME, signature));
        }
        return new GameProfile(claim.getForwardedUuid(), claim.getPlayerName(), new PropertyMap(properties));
    }
}
