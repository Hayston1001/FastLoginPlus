/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

/** Optional notification-only property, independent of the unchanged identity claim. */
public final class CrackedNoticeClaim {
    public static final String PROPERTY_NAME = "flp_cracked_notice";
    private final boolean owed;
    private final String name;
    private final UUID playerId;
    private final UUID nonce;

    public CrackedNoticeClaim(boolean owed, String name, UUID playerId, UUID nonce) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Invalid notice name");
        }
        this.owed = owed;
        this.name = name;
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.nonce = Objects.requireNonNull(nonce, "nonce");
    }

    public String encode() {
        return "1:" + (owed ? "Y" : "N") + ":" + name + ":" + playerId + ":" + nonce;
    }

    /**
     * Missing means no work; notification callers ignore malformed or duplicate properties.
     *
     * @param values notification property values from the forwarding profile
     * @param expectedName actual forwarded player name
     * @param expectedId actual forwarded player UUID
     * @return the optional notice, or null when absent
     */
    public static CrackedNoticeClaim parse(Iterable<String> values, String expectedName, UUID expectedId) {
        Iterator<String> iterator = values.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        String value = iterator.next();
        if (iterator.hasNext() || value == null || value.length() > 96) {
            throw new IllegalArgumentException("Invalid notice property count or length");
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 5 || !"1".equals(parts[0]) || !("Y".equals(parts[1]) || "N".equals(parts[1]))) {
            throw new IllegalArgumentException("Unsupported notice property");
        }
        CrackedNoticeClaim claim = new CrackedNoticeClaim("Y".equals(parts[1]), parts[2],
                uuid(parts[3]), uuid(parts[4]));
        if (!claim.name.equals(expectedName) || !claim.playerId.equals(expectedId)) {
            throw new IllegalArgumentException("Notice does not match forwarding profile");
        }
        return claim;
    }

    private static UUID uuid(String text) {
        UUID id = UUID.fromString(text);
        if (!id.toString().equals(text)) {
            throw new IllegalArgumentException("Invalid notice UUID");
        }
        return id;
    }

    public boolean accepts(IdentityClaim identity) {
        return owed && identity.getDecision() == IdentityClaim.Decision.OFFLINE && identity.isSameNameCracked()
                && name.equals(identity.getPlayerName()) && playerId.equals(identity.getForwardedUuid());
    }

    public CrackedNoticeWire acknowledgement(String proxyIds) {
        return new CrackedNoticeWire(CrackedNoticeWire.Kind.ACK, name, playerId, proxyIds, nonce);
    }

    public boolean matches(CrackedNoticeWire ack) {
        return ack != null && ack.kind() == CrackedNoticeWire.Kind.ACK && name.equals(ack.name())
                && playerId.equals(ack.playerId()) && nonce.equals(ack.nonce());
    }
}
