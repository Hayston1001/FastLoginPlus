/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib.packet;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.StringJoiner;

public class ClientPublicKey {

    private final Instant expiry;
    private final PublicKey key;
    private final byte[] signature;

    public Instant expiry() {
        return expiry;
    }

    public PublicKey key() {
        return key;
    }

    public byte[] signature() {
        return signature;
    }

    public ClientPublicKey(Instant expiry, PublicKey key, byte[] signature) {
        this.expiry = expiry;
        this.key = key;
        this.signature = signature;
    }

    public static ClientPublicKey of(Instant expiry, PublicKey key, byte[] signature) {
        return new ClientPublicKey(expiry, key, signature);
    }

    public boolean isExpired(Instant verifyTimestamp) {
        return !verifyTimestamp.isBefore(expiry);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ClientPublicKey.class.getSimpleName() + '[', "]")
                .add("expiry=" + expiry)
                .add("key=" + Base64.getEncoder().encodeToString(key.getEncoded()))
                .add("signature=" + Base64.getEncoder().encodeToString(signature))
                .toString();
    }
}
