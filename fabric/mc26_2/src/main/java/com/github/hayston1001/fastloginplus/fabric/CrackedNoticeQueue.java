/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeWire;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;

/** One connection's queue for the first same-name cracked notice, not an account flag. */
final class CrackedNoticeQueue {
    private final CrackedNoticeWire capability;
    private boolean queued;
    private boolean joined;
    private boolean sent;

    CrackedNoticeQueue(CrackedNoticeWire capability) {
        this.capability = capability;
    }

    CrackedNoticeWire capability() {
        return capability;
    }

    boolean acceptsClaim(IdentityClaim claim) {
        return claim.getDecision() == IdentityClaim.Decision.OFFLINE && claim.isSameNameCracked()
                && capability.name().equals(claim.getPlayerName())
                && capability.playerId().equals(claim.getForwardedUuid());
    }

    synchronized void accept(CrackedNoticeWire delivery, IdentityClaim claim) {
        if (delivery.kind() == CrackedNoticeWire.Kind.DELIVER
                && capability.sameDelivery(delivery) && acceptsClaim(claim)) {
            queued = true;
        }
    }

    synchronized void joined(IdentityClaim claim) {
        joined = acceptsClaim(claim);
    }

    synchronized boolean take() {
        if (!joined || !queued || sent) {
            return false;
        }
        sent = true;
        return true;
    }
}
