/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BedrockClaimTest {
    private final UUID proxy = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final long now = System.currentTimeMillis();

    private BedrockClaim claim(long time) {
        return new BedrockClaim("Bedrock", player, true, "linked", "no-conflict", "false", time, UUID.randomUUID());
    }

    @Test
    void roundTripAndSeparateBackwardCompatibleProperty() {
        BedrockClaim source = claim(now);
        String wire = source.encode(proxy);
        BedrockClaim decoded = BedrockClaim.verify(wire, "Bedrock", player, Collections.singleton(proxy), now);
        assertTrue(decoded.isLinked());
        assertEquals("linked", decoded.getAutoLogin());
        assertEquals("no-conflict", decoded.getAutoRegister());
        assertEquals("false", decoded.getAllowConflict());
        assertEquals(source.getNonce(), decoded.getNonce());
        assertFalse(wire.contains(proxy.toString()));
        assertTrue(BedrockClaim.PROPERTY_NAME.matches("\\w{1,16}"));
        assertNotEquals(IdentityClaim.PROPERTY_NAME, BedrockClaim.PROPERTY_NAME);
        IdentityClaim identity = new IdentityClaim(IdentityClaim.Decision.OFFLINE, "Bedrock", player, null);
        assertEquals(identity.encode(), IdentityClaim.parseExactlyOne(Collections.singleton(identity.encode()),
                "Bedrock", player).encode());
    }

    @Test
    void rejectsForgedUnsignedUnboundDisallowedAndExpiredClaims() {
        String wire = claim(now).encode(proxy);
        assertRejected(wire.replace(":linked:", ":true:"), "Bedrock", player, proxy);
        assertRejected(wire.substring(0, wire.lastIndexOf(':')), "Bedrock", player, proxy);
        assertRejected(wire, "Other", player, proxy);
        assertRejected(wire, "Bedrock", UUID.randomUUID(), proxy);
        assertRejected(wire, "Bedrock", player, UUID.randomUUID());
        assertRejected(claim(now - BedrockClaim.MAX_AGE_MILLIS - 1).encode(proxy), "Bedrock", player, proxy);
        assertRejected(claim(now + 5_001).encode(proxy), "Bedrock", player, proxy);
        assertRejected(wire.replace(":Y:", ":N:"), "Bedrock", player, proxy);
        assertThrows(IllegalArgumentException.class, () -> BedrockClaim.verify(wire, "Bedrock", player,
                Collections.emptyList(), now));
        String payload = wire.substring(0, wire.lastIndexOf(':'));
        assertRejected(payload + ":" + IdentityClaim.sign(payload, proxy), "Bedrock", player, proxy);
    }

    private void assertRejected(String wire, String name, UUID uuid, UUID allowed) {
        assertThrows(IllegalArgumentException.class,
                () -> BedrockClaim.verify(wire, name, uuid, Collections.singleton(allowed), now));
    }
}
