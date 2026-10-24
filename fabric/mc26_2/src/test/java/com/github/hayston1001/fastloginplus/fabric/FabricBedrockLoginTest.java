/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.BedrockClaim;
import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FabricBedrockLoginTest {
    static final UUID UUID_B = UUID.fromString("00000000-0000-0000-0009-01eed05d107b");
    static final UUID PROXY = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    static final long NOW = 1_790_000_000_000L;
    private final FabricBedrockLogin login = new FabricBedrockLogin();
    private final Object connection = new Object();

    static EasyAuth344Bridge.BedrockOwner owner() {
        return new EasyAuth344Bridge.BedrockOwner(
                new BedrockAccount(".Name", UUID_B, "stored-hash", "UNKNOWN", null), new Object());
    }

    static String claim(boolean linked, String policy, long time) {
        return new BedrockClaim(".Name", UUID_B, linked, policy, "true", "true", time, UUID.randomUUID())
                .encode(PROXY);
    }

    static GameProfile profile(String value, int copies, String name, UUID uuid) {
        var properties = ArrayListMultimap.<String, Property>create();
        for (int i = 0; i < copies; i++) {
            properties.put(BedrockClaim.PROPERTY_NAME, new Property(BedrockClaim.PROPERTY_NAME, value));
        }
        return new GameProfile(uuid, name, new PropertyMap(properties));
    }

    static GameProfile profile(String value) {
        return profile(value, 1, ".Name", UUID_B);
    }

    private void capture(GameProfile profile) {
        login.capture(connection, profile, PROXY.toString(), NOW, FabricBedrockLoginTest::owner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "no-conflict"})
    void completedOwnerIsBoundToActualConnectionAndPlayerExactlyOnce(String policy) {
        GameProfile profile = profile(claim(false, policy, NOW));
        capture(profile);
        Object player = new Object();
        assertNotNull(login.take(connection, player, profile, NOW));
        assertNull(login.take(connection, player, profile, NOW));
        assertThrows(RuntimeException.class, () -> login.take(connection, new Object(), profile, NOW));
        assertThrows(RuntimeException.class, () -> login.take(new Object(), player, profile, NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "linked"})
    void proxyPolicyCannotBeOverriddenByRegistrationOrConflict(String policy) {
        assertThrows(FabricMessages.PlayerFailure.class, () -> capture(profile(claim(false, policy, NOW))));
    }

    @Test
    void linkedAccountNeverAuthorizesThisSubset() {
        assertThrows(FabricMessages.PlayerFailure.class, () -> capture(profile(claim(true, "true", NOW))));
    }

    @Test
    void absentAccountAndPlaceholderNeverBecomePending() {
        var profile = profile(claim(false, "true", NOW));
        assertThrows(FabricMessages.PlayerFailure.class,
                () -> login.capture(connection, profile, PROXY.toString(), NOW, () -> null));
        assertThrows(FabricMessages.PlayerFailure.class, () -> login.take(connection, new Object(), profile, NOW));
        var empty = new EasyAuth344Bridge.BedrockOwner(new BedrockAccount(".Name", UUID_B, "", "UNKNOWN", null), new Object());
        assertThrows(FabricMessages.PlayerFailure.class, () -> login.capture(connection,
                profile(claim(false, "true", NOW)), PROXY.toString(), NOW, () -> empty));
    }

    @Test
    void invalidClaimsCannotEvenReadAnAccount() {
        String valid = claim(false, "true", NOW);
        AtomicInteger reads = new AtomicInteger();
        var profiles = java.util.List.of(profile(valid, 0, ".Name", UUID_B), profile(valid, 2, ".Name", UUID_B),
                profile(valid, 1, "Other", UUID_B), profile(valid, 1, ".Name", UUID.randomUUID()),
                profile("unsigned"), profile(valid.substring(0, valid.lastIndexOf(':'))),
                profile(claim(false, "true", NOW - BedrockClaim.MAX_AGE_MILLIS - 1)),
                profile(claim(false, "true", NOW + 5_001)), profile(valid.replace(":true:", ":false:")));
        for (var profile : profiles) {
            assertThrows(BedrockClaimException.class, () -> login.capture(connection, profile, PROXY.toString(), NOW,
                    () -> { reads.incrementAndGet(); return owner(); }));
        }
        assertThrows(AuthUnavailableException.class, () -> login.capture(connection, profile(valid), "", NOW,
                () -> { reads.incrementAndGet(); return owner(); }));
        assertThrows(BedrockClaimException.class, () -> login.capture(connection, profile(valid),
                UUID.randomUUID().toString(), NOW, () -> { reads.incrementAndGet(); return owner(); }));
        assertThrows(AuthUnavailableException.class, () -> login.capture(connection, profile(valid),
                "not-a-proxy-id", NOW, () -> { reads.incrementAndGet(); return owner(); }));
        assertEquals(0, reads.get());
    }

    @Test
    void replayedClaimIsAnInvalidClaimButStillNeverReadsAnAccount() {
        GameProfile profile = profile(claim(false, "true", NOW));
        capture(profile);
        AtomicInteger reads = new AtomicInteger();

        assertThrows(BedrockClaimException.class, () -> login.capture(new Object(), profile,
                PROXY.toString(), NOW, () -> { reads.incrementAndGet(); return owner(); }));
        assertEquals(0, reads.get());
    }

    @Test
    void sameNameReconnectNeedsFreshClaimAndCannotInheritOrReplay() {
        GameProfile profile = profile(claim(false, "true", NOW));
        capture(profile);
        login.disconnect(connection);
        assertThrows(FabricMessages.PlayerFailure.class, () -> login.take(connection, new Object(), profile, NOW));
        assertThrows(BedrockClaimException.class, () -> login.capture(new Object(), profile, PROXY.toString(), NOW,
                FabricBedrockLoginTest::owner));
        Object next = new Object();
        var fresh = profile(claim(false, "true", NOW));
        login.capture(next, fresh, PROXY.toString(), NOW, FabricBedrockLoginTest::owner);
        assertNotNull(login.take(next, new Object(), fresh, NOW));
    }

    @Test
    void ageIsRecheckedAtJoinAndWrongProfileCannotConsumeTheOwner() {
        var old = profile(claim(false, "true", NOW - BedrockClaim.MAX_AGE_MILLIS + 1));
        capture(old);
        assertThrows(BedrockClaimException.class, () -> login.take(connection, new Object(), old, NOW + 2));
        var fresh = profile(claim(false, "true", NOW));
        capture(fresh);
        assertThrows(FabricMessages.PlayerFailure.class, () -> login.take(connection, new Object(),
                new GameProfile(UUID.randomUUID(), ".Name"), NOW));
    }

    @Test
    void unreadableAccountCannotLeavePendingState() {
        var profile = profile(claim(false, "true", NOW));
        assertThrows(AuthUnavailableException.class, () -> login.capture(connection, profile, PROXY.toString(), NOW,
                () -> { throw new AuthUnavailableException("SQLITE_BUSY: database is locked"); }));
        assertThrows(FabricMessages.PlayerFailure.class, () -> login.take(connection, new Object(), profile, NOW));
    }

    @Test
    void disconnectWhileAccountReadIsPendingCannotResurrectAttempt() {
        var profile = profile(claim(false, "true", NOW));
        assertThrows(RuntimeException.class, () -> login.capture(connection, profile, PROXY.toString(), NOW,
                () -> {
                    login.disconnect(connection);
                    return owner();
                }));
        assertThrows(RuntimeException.class, () -> login.take(connection, new Object(), profile, NOW));
    }

    @Test
    void serverThreadAccountReadDoesNotHoldGlobalConnectionLock() {
        var profile = profile(claim(false, "true", NOW));
        login.capture(connection, profile, PROXY.toString(), NOW, () -> {
            assertDoesNotThrow(() -> java.util.concurrent.CompletableFuture.runAsync(
                    () -> login.disconnect(new Object())).get(2, java.util.concurrent.TimeUnit.SECONDS));
            return owner();
        });
        assertNotNull(login.take(connection, new Object(), profile, NOW));
    }
}
