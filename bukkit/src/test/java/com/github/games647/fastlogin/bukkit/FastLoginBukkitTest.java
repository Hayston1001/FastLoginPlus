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
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.core.CommonUtil;
import java.util.UUID;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import com.github.games647.craftapi.UUIDAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastLoginBukkitTest {

    /**
     * The warning exists for one situation only: a direct connection whose
     * AuthMe verification is off <em>because</em> PacketEvents is missing. Proxy backends were
     * added as an explicit exclusion on 2026-09-15 — there AuthMe's listener never registers,
     * upstream prints no misleading warning, and the command layer is already covered by
     * {@code AuthMeCommandGuard}, so the line was pure noise (plus one sentence about a warning
     * that cannot appear in that setup).
     */
    @Test
    void commandLayerWarningOnlyAppliesToDirectConnectionsWithoutPacketEvents() {
        // the only case it is meant for
        assertTrue(FastLoginBukkit.shouldWarnAboutCommandLayer(true, false, false));

        // proxy backend: nothing surprising happened, nothing upstream to disambiguate
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(true, false, true));

        // PacketEvents present: AuthMe's own verification is active
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(true, true, false));
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(true, true, true));

        // no takeover (AuthMe 5.x, absent, or half-applied): the command layer is AuthMe's own
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(false, false, false));
    }

    @Test
    void testRGB() {
        String message = "&x00002a00002b&lText";
        String msg = CommonUtil.translateColorCodes(message);
        assertEquals(msg, "§x00002a00002b§lText");

        @SuppressWarnings("deprecation")
        BaseComponent[] components = TextComponent.fromLegacyText(msg);
        String expected = "{\"bold\":true,\"color\":\"#00a00b\",\"text\":\"Text\"}";
        //noinspection deprecation
        assertEquals(ComponentSerializer.toString(components), expected);
    }

    /**
     * Paper caches the fully filled profile — injected properties included — and indexes
     * it by name <em>and</em> UUID. Because {@code premiumUuid: false} rewrites a premium login to
     * the name-derived offline UUID, a cracked login with the same name carries the identical UUID
     * and could be served that cached profile; the backend would then treat it as proxy-attested
     * premium and create AuthMe records for it. Requiring the attestation to match the one carried
     * at pre-login makes it single-use per login.
     */
    @Test
    void attestationMustMatchTheOneCarriedAtPreLogin() {
        UUID attested = UUID.fromString("272cb3e9-24d3-4dcd-b47c-4b786e7421f8");
        UUID other = UUID.fromString("08c85864-b91b-3ebc-a6ab-f95e7449c594");

        // the normal case: the proxy attested this very login
        assertEquals(attested, FastLoginBukkit.resolveAttestedUuid(attested, attested));

        // a cached profile shows up at configure time although this login carried nothing
        assertNull(FastLoginBukkit.resolveAttestedUuid(attested, null));

        // stale entry from an earlier login still in the map
        assertNull(FastLoginBukkit.resolveAttestedUuid(attested, other));

        // no attestation at configure time stays "not attested", never a resurrected map entry
        assertNull(FastLoginBukkit.resolveAttestedUuid(null, attested));
        assertNull(FastLoginBukkit.resolveAttestedUuid(null, null));
    }

    /**
     * With {@code premiumUuid: true} the proxy keeps the Mojang UUID and attaches no
     * attestation property, so the configuration phase has to recognise the forwarded UUID itself
     * instead of falling back to the asynchronous Mojang lookup - that lookup is what let AuthMe's
     * preJoin dialog appear before the record existed.
     */
    @Test
    void forwardedUuidIsUsedWhenNoPropertyWasAttached() {
        UUID mojangUuid = UUID.fromString("272cb3e9-24d3-4dcd-b47c-4b786e7421f8");
        UUID offlineUuid = UUIDAdapter.generateOfflineId("Hayston1001");

        // premiumUuid: true - no property, but the connection carries the proxy's Mojang UUID
        assertTrue(FastLoginBukkit.usesForwardedUuidAttestation(null, mojangUuid, "Hayston1001"));

        // premiumUuid: false - the property is present and stays the attestation (checked before)
        assertFalse(FastLoginBukkit.usesForwardedUuidAttestation(mojangUuid, offlineUuid, "Hayston1001"));

        // a cracked login: offline UUID, nothing forwarded
        assertFalse(FastLoginBukkit.usesForwardedUuidAttestation(null, offlineUuid, "Hayston1001"));

        // direct connection (no proxy involved): nothing to recognise
        assertFalse(FastLoginBukkit.usesForwardedUuidAttestation(null, mojangUuid, null));
        assertFalse(FastLoginBukkit.usesForwardedUuidAttestation(null, null, "Hayston1001"));
    }

    /**
     * A login that is already in flight keeps a premium marking running on an async task,
     * which can reach the configure-phase marking <em>after</em> {@code /flp cracked} deleted the
     * record — and would then re-create it with the Mojang UUID and no password, leaving the player
     * able to neither log in nor register. The administrative switch therefore wins for a window.
     */
    @Test
    void administratorCrackedSwitchSuppressesPremiumMarkingForAWindow() {
        long now = 1_000_000L;
        long ttl = 30_000L;

        // never switched: nothing to suppress
        assertFalse(FastLoginBukkit.isCrackedOverrideActive(now, null, ttl));

        // the in-flight marking lands right after the command
        assertTrue(FastLoginBukkit.isCrackedOverrideActive(now, now, ttl));
        assertTrue(FastLoginBukkit.isCrackedOverrideActive(now + 2_500L, now, ttl));

        // still inside the window at the boundary, outside one millisecond later
        assertTrue(FastLoginBukkit.isCrackedOverrideActive(now + ttl, now, ttl));
        assertFalse(FastLoginBukkit.isCrackedOverrideActive(now + ttl + 1L, now, ttl));
    }
}
