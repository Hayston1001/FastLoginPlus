/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.BedrockClaim;
import com.mojang.authlib.GameProfile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** One verified attempt per actual connection, never keyed by player name. */
final class FabricBedrockLogin {
    private final Map<Object, Attempt> pending = new IdentityHashMap<>();
    private final Map<Object, Object> captures = new IdentityHashMap<>();
    private final Map<UUID, Long> nonces = new HashMap<>();

    static final class Attempt {
        final String name;
        final UUID uuid;
        final long deadline;
        final EasyAuth344Bridge.BedrockOwner owner;
        final String encoded;
        final java.util.List<UUID> allowed;
        Object player;

        Attempt(GameProfile profile, long deadline, EasyAuth344Bridge.BedrockOwner owner,
                String encoded, java.util.List<UUID> allowed) {
            name = profile.name();
            uuid = profile.id();
            this.deadline = deadline;
            this.owner = owner;
            this.encoded = encoded;
            this.allowed = allowed;
        }
    }

    static boolean hasClaim(GameProfile profile) {
        return !profile.properties().get(BedrockClaim.PROPERTY_NAME).isEmpty();
    }

    void capture(Object connection, GameProfile profile, String allowedIds, long now,
                 java.util.function.Supplier<EasyAuth344Bridge.BedrockOwner> account) {
        Object ticket = new Object();
        synchronized (this) {
            pending.remove(connection);
            captures.put(connection, ticket);
        }
        try {
            capture(connection, profile, allowedIds, now, account, ticket);
        } finally {
            synchronized (this) {
                captures.remove(connection, ticket);
            }
        }
    }

    private void capture(Object connection, GameProfile profile, String allowedIds, long now,
                         java.util.function.Supplier<EasyAuth344Bridge.BedrockOwner> account, Object ticket) {
        var properties = profile.properties().get(BedrockClaim.PROPERTY_NAME);
        if (properties.size() != 1) {
            throw new IllegalArgumentException("Exactly one signed Bedrock claim is required");
        }
        var allowed = Arrays.stream(allowedIds.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).map(UUID::fromString).toList();
        String encoded = properties.iterator().next().value();
        BedrockClaim claim = BedrockClaim.verify(encoded,
                profile.name(), profile.id(), allowed, now);
        synchronized (this) {
            nonces.values().removeIf(expiry -> expiry < now);
            if (nonces.containsKey(claim.getNonce()) || nonces.size() >= 10_000) {
                throw new IllegalArgumentException("Replayed Bedrock claim or replay cache capacity exceeded");
            }
            // Retain even denied nonces. A policy/account failure never makes a claim reusable.
            long deadline = now + BedrockClaim.MAX_AGE_MILLIS + 5_000L;
            nonces.put(claim.getNonce(), deadline);
        }
        if (claim.isLinked() || !("true".equals(claim.getAutoLogin())
                || "no-conflict".equals(claim.getAutoLogin()))) {
            throw FabricMessages.failure("bedrock-unsupported");
        }
        var owner = account.get();
        if (owner == null || !owner.account().owns(profile.name(), profile.id())) {
            throw FabricMessages.failure("bedrock-unsupported");
        }
        synchronized (this) {
            if (captures.get(connection) != ticket) {
                throw new IllegalStateException("Bedrock configuration attempt disconnected or was replaced");
            }
            pending.put(connection, new Attempt(profile, now + BedrockClaim.MAX_AGE_MILLIS, owner, encoded, allowed));
        }
    }

    synchronized Attempt take(Object connection, Object player, GameProfile profile, long now) {
        Attempt attempt = pending.get(connection);
        if (attempt == null || !attempt.name.equals(profile.name()) || !attempt.uuid.equals(profile.id())
                || now > attempt.deadline) {
            throw FabricMessages.failure("bedrock-unsupported");
        }
        if (attempt.player != null) {
            if (attempt.player == player) {
                return null;
            }
            throw FabricMessages.failure("bedrock-unsupported");
        }
        // Consume before account reads/persistence, including every failure path.
        attempt.player = player;
        BedrockClaim.verify(attempt.encoded, profile.name(), profile.id(), attempt.allowed, now);
        return attempt;
    }

    synchronized void disconnect(Object connection) {
        pending.remove(connection);
        captures.remove(connection);
    }
}
