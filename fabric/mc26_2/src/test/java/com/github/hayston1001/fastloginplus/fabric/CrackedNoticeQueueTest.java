/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeWire;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrackedNoticeQueueTest {
    private final UUID uuid = UUID.nameUUIDFromBytes("OfflinePlayer:TestUser".getBytes(StandardCharsets.UTF_8));
    private final CrackedNoticeWire capability = new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
            "TestUser", uuid, UUID.randomUUID().toString(), UUID.randomUUID());

    private IdentityClaim offline(boolean marked) {
        return new IdentityClaim(IdentityClaim.Decision.OFFLINE, "TestUser", uuid, null,
                new IdentityClaim.Facts(false, marked, IdentityClaim.Consent.NO, true, true, false));
    }

    @Test
    void repeatedHandoffBeforeJoinQueuesOneChatAndCannotSendDuringConfiguration() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(capability);
        queue.accept(capability.withKind(CrackedNoticeWire.Kind.DELIVER), offline(true));
        queue.accept(capability.withKind(CrackedNoticeWire.Kind.DELIVER), offline(true));
        assertFalse(queue.take());
        queue.joined(offline(true));
        assertTrue(queue.take());
        assertFalse(queue.take());
        queue.accept(capability.withKind(CrackedNoticeWire.Kind.DELIVER), offline(true));
        assertFalse(queue.take());
    }

    @Test
    void handoffAfterJoinAlsoSendsOnlyOnce() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(capability);
        queue.joined(offline(true));
        assertFalse(queue.take());
        queue.accept(capability.withKind(CrackedNoticeWire.Kind.DELIVER), offline(true));
        assertTrue(queue.take());
        assertFalse(queue.take());
    }

    @Test
    void concurrentDuplicateHandoffsHaveOneQueueConsumer() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(capability);
        queue.joined(offline(true));
        java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger();
        java.util.stream.IntStream.range(0, 16).parallel().forEach(index -> {
            queue.accept(capability.withKind(CrackedNoticeWire.Kind.DELIVER), offline(true));
            if (queue.take()) {
                sent.incrementAndGet();
            }
        });
        assertEquals(1, sent.get());
    }

    @Test
    void persistentFactAndCapabilityAloneNeverInventAFirstConfirmation() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(capability);
        queue.accept(capability, offline(true));
        queue.joined(offline(true));
        assertFalse(queue.take());
        CrackedNoticeQueue reconnect = new CrackedNoticeQueue(new CrackedNoticeWire(CrackedNoticeWire.Kind.CAPABLE,
                "TestUser", uuid, capability.sourceProxyIds(), UUID.randomUUID()));
        reconnect.joined(offline(true));
        reconnect.accept(capability.withKind(CrackedNoticeWire.Kind.DELIVER), offline(true));
        assertFalse(reconnect.take());
    }

    @Test
    void wrongNameUuidConfirmationAndVerifiedOrClearedClaimsCannotQueue() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(capability);
        CrackedNoticeWire delivery = capability.withKind(CrackedNoticeWire.Kind.DELIVER);
        queue.accept(new CrackedNoticeWire(delivery.kind(), "OtherUser", uuid,
                capability.sourceProxyIds(), capability.nonce()), offline(true));
        queue.accept(new CrackedNoticeWire(delivery.kind(), "TestUser", UUID.randomUUID(),
                capability.sourceProxyIds(), capability.nonce()), offline(true));
        queue.accept(delivery, offline(false));
        queue.accept(delivery, new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, "TestUser", uuid, uuid));
        queue.joined(offline(true));
        assertFalse(queue.take());
    }
}
