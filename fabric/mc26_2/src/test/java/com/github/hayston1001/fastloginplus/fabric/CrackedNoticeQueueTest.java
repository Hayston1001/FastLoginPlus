/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeClaim;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrackedNoticeQueueTest {
    private final UUID uuid = UUID.nameUUIDFromBytes("OfflinePlayer:TestUser".getBytes(StandardCharsets.UTF_8));
    private final CrackedNoticeClaim notice = new CrackedNoticeClaim(true, "TestUser", uuid, UUID.randomUUID());

    private IdentityClaim offline(boolean marked) {
        return new IdentityClaim(IdentityClaim.Decision.OFFLINE, "TestUser", uuid, null,
                new IdentityClaim.Facts(false, marked, IdentityClaim.Consent.NO, true, true, false));
    }

    @Test
    void joinRendersBeforeAcknowledgingAndNeverRepeats() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(notice);
        Runnable render = mock(Runnable.class);
        Runnable ack = mock(Runnable.class);
        verifyNoInteractions(render, ack);
        queue.renderAfterJoin(offline(true), render, ack);
        queue.renderAfterJoin(offline(true), render, ack);
        org.mockito.InOrder order = inOrder(render, ack);
        order.verify(render).run();
        order.verify(ack).run();
        verifyNoMoreInteractions(render, ack);
    }

    @Test
    void missingOrFalsePropertyDoesNotInventAFirstConfirmationFromPersistentFacts() {
        Runnable render = mock(Runnable.class);
        Runnable ack = mock(Runnable.class);
        new CrackedNoticeQueue(null).renderAfterJoin(offline(true), render, ack);
        new CrackedNoticeQueue(new CrackedNoticeClaim(false, "TestUser", uuid, UUID.randomUUID()))
                .renderAfterJoin(offline(true), render, ack);
        verifyNoInteractions(render, ack);
    }

    @Test
    void mismatchedAndVerifiedIdentitiesNeverRenderOrAcknowledge() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(notice);
        Runnable render = mock(Runnable.class);
        Runnable ack = mock(Runnable.class);
        queue.renderAfterJoin(offline(false), render, ack);
        queue.renderAfterJoin(new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED,
                "TestUser", uuid, uuid), render, ack);
        new CrackedNoticeQueue(new CrackedNoticeClaim(true, "OtherUser", uuid, UUID.randomUUID()))
                .renderAfterJoin(offline(true), render, ack);
        new CrackedNoticeQueue(new CrackedNoticeClaim(true, "TestUser", UUID.randomUUID(), UUID.randomUUID()))
                .renderAfterJoin(offline(true), render, ack);
        verifyNoInteractions(render, ack);
    }

    @Test
    void renderFailureDoesNotAcknowledgeOrRetryAnUncertainSend() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(notice);
        Runnable render = mock(Runnable.class);
        Runnable ack = mock(Runnable.class);
        doThrow(new IllegalStateException("write failed")).when(render).run();
        assertThrows(IllegalStateException.class, () -> queue.renderAfterJoin(offline(true), render, ack));
        queue.renderAfterJoin(offline(true), render, ack);
        verify(render).run();
        verifyNoInteractions(ack);
    }

    @Test
    void lostAcknowledgementNeverRepeatsBackendChat() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(notice);
        Runnable render = mock(Runnable.class);
        Runnable ack = mock(Runnable.class);
        doThrow(new IllegalStateException("ACK lost")).when(ack).run();
        assertThrows(IllegalStateException.class, () -> queue.renderAfterJoin(offline(true), render, ack));
        queue.renderAfterJoin(offline(true), render, ack);
        verify(render).run();
        verify(ack).run();
    }

    @Test
    void concurrentJoinsHaveOneConsumer() {
        CrackedNoticeQueue queue = new CrackedNoticeQueue(notice);
        Runnable render = mock(Runnable.class);
        Runnable ack = mock(Runnable.class);
        java.util.stream.IntStream.range(0, 16).parallel().forEach(index ->
                queue.renderAfterJoin(offline(true), render, ack));
        verify(render).run();
        verify(ack).run();
    }
}
