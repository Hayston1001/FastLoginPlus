/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeClaim;
import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeWire;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FabricNoticeProfileTest {
    @TempDir
    Path directory;
    private final UUID id = UUID.nameUUIDFromBytes("OfflinePlayer:TestUser".getBytes(StandardCharsets.UTF_8));
    private final CrackedNoticeClaim notice = new CrackedNoticeClaim(true, "TestUser", id, UUID.randomUUID());
    private final IdentityClaim identity = new IdentityClaim(IdentityClaim.Decision.OFFLINE, "TestUser", id, null,
            new IdentityClaim.Facts(false, true, IdentityClaim.Consent.NO, true, true, false));

    private GameProfile profile(String... values) {
        ArrayListMultimap<String, Property> properties = ArrayListMultimap.create();
        properties.put(IdentityClaim.PROPERTY_NAME, new Property(IdentityClaim.PROPERTY_NAME, identity.encode()));
        for (String value : values) {
            properties.put(CrackedNoticeClaim.PROPERTY_NAME, new Property(CrackedNoticeClaim.PROPERTY_NAME, value));
        }
        return new GameProfile(id, "TestUser", new PropertyMap(properties));
    }

    @Test
    void readsIndependentPropertyWithoutChangingIdentityAndAbsenceIsOptional() {
        assertNull(FabricCrackedNotice.readNotice(profile()));
        GameProfile profile = profile(notice.encode());
        assertEquals(notice.encode(), FabricCrackedNotice.readNotice(profile).encode());
        assertEquals(identity.encode(), FastLoginFabric.parseClaim(profile).encode());
    }

    @Test
    void malformedNotificationDoesNotAlterTheSeparateIdentityClaim() {
        for (GameProfile profile : List.of(profile("bad"), profile(notice.encode(), notice.encode()))) {
            assertThrows(IllegalArgumentException.class, () -> FabricCrackedNotice.readNotice(profile));
            assertEquals(identity.encode(), FastLoginFabric.parseClaim(profile).encode());
        }
    }

    @Test
    void everyIdentityAnnouncesRenderingEvenWithoutNotificationDebt() throws Exception {
        for (IdentityClaim.Decision decision : List.of(IdentityClaim.Decision.OFFLINE,
                IdentityClaim.Decision.PREMIUM_VERIFIED)) {
            IdentityClaim noDebt = new IdentityClaim(decision, "TestUser", id,
                    decision == IdentityClaim.Decision.PREMIUM_VERIFIED ? id : null);
            List<byte[]> announcements = new ArrayList<>();
            FabricCrackedNotice.announce(noDebt, announcements::add, "trusted-proxy");
            FabricCrackedNotice.announce(noDebt, announcements::add, "trusted-proxy");
            assertEquals(2, announcements.size());
            CrackedNoticeWire first = CrackedNoticeWire.decode(announcements.getFirst());
            CrackedNoticeWire second = CrackedNoticeWire.decode(announcements.getLast());
            assertEquals(CrackedNoticeWire.Kind.CAPABLE, first.kind());
            assertEquals("TestUser", first.name());
            assertEquals(id, first.playerId());
            assertEquals("trusted-proxy", first.sourceProxyIds());
            assertNotEquals(first.nonce(), second.nonce());
        }
    }

    @Test
    void backendSelectedLanguageIsRenderedOnceBeforeNonceBoundAck() throws Exception {
        Field messages = FabricMessages.class.getDeclaredField("messages");
        messages.setAccessible(true);
        Map<String, String> original = (Map<String, String>) messages.get(null);
        try {
            for (String language : List.of("en", "zh")) {
                Map<String, String> selected = FabricMessages.load(language, directory);
                messages.set(null, selected);
                List<String> rendered = new ArrayList<>();
                List<byte[]> acknowledgements = new ArrayList<>();
                CrackedNoticeClaim parsed = FabricCrackedNotice.readNotice(profile(notice.encode()));
                CrackedNoticeQueue queue = new CrackedNoticeQueue(parsed);
                java.util.function.Consumer<byte[]> acknowledge = bytes -> {
                    assertEquals(1, rendered.size());
                    acknowledgements.add(bytes);
                };
                FabricCrackedNotice.renderAfterJoin(queue, identity,
                        text -> rendered.add(text.getString()), acknowledge, "trusted-proxy");
                FabricCrackedNotice.renderAfterJoin(queue, identity,
                        text -> rendered.add(text.getString()), acknowledge, "trusted-proxy");
                assertEquals(List.of(selected.get("same-name-cracked-recorded")), rendered);
                assertEquals(1, acknowledgements.size());
                CrackedNoticeWire ack = CrackedNoticeWire.decode(acknowledgements.getFirst());
                assertTrue(parsed.matches(ack));
                assertEquals("trusted-proxy", ack.sourceProxyIds());
            }
        } finally {
            messages.set(null, original);
        }
    }
}
