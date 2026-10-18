/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class BindingClaimTest {
    private static final String NAME = "TestUser";
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @ParameterizedTest
    @EnumSource(IdentityClaim.Consent.class)
    void laterVerifiedConnectionClaimsOnlyWithRecordedConsent(IdentityClaim.Consent consent) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyAuth.DB = new Database();
        Entry entry = EasyAuth.DB.entry;
        var pending = new ConcurrentHashMap<String, EasyAuth344Bridge.PendingBinding>();
        pending.put(NAME, new EasyAuth344Bridge.PendingBinding(OFFLINE, entry.password, TARGET, () -> 0L));
        EasyAuth344Bridge bridge = mock(EasyAuth344Bridge.class, CALLS_REAL_METHODS);
        doNothing().when(bridge).verifyPolicy();
        set(bridge, "easyAuth", EasyAuth.class);
        set(bridge, "entryType", Entry.class);
        set(bridge, "playerAuth", PlayerAuth.class);
        set(bridge, "ipLimit", IpLimit.class);
        set(bridge, "pendingBindings", pending);

        ServerPlayer player = mock(ServerPlayer.class, withSettings().extraInterfaces(PlayerAuth.class));
        when(player.getGameProfile()).thenReturn(new GameProfile(TARGET, NAME));
        PlayerAuth auth = (PlayerAuth) player;
        AtomicBoolean authenticated = new AtomicBoolean();
        when(auth.easyAuth$isAuthenticated()).thenAnswer(call -> authenticated.get());
        when(auth.easyAuth$getPlayerEntryV1()).thenReturn(entry);
        when(auth.easyAuth$getIpAddress()).thenReturn("127.0.0.2");
        doAnswer(call -> {
            assertEquals(1, EasyAuth.DB.writes);
            authenticated.set(call.getArgument(0));
            return null;
        }).when(auth).easyAuth$setAuthenticated(true);
        IdentityClaim claim = new IdentityClaim(IdentityClaim.Decision.PREMIUM_VERIFIED, NAME, TARGET, TARGET,
                new IdentityClaim.Facts(false, false, consent, true, false, false));

        bridge.onJoin(player, claim);

        boolean accepted = consent == IdentityClaim.Consent.YES;
        assertEquals(accepted, authenticated.get());
        assertEquals(accepted ? 1 : 0, EasyAuth.DB.writes);
        assertEquals(!accepted, pending.containsKey(NAME));
        assertEquals(accepted ? "" : "old-hash", entry.password);
        assertEquals(accepted ? TARGET : OFFLINE, entry.uuid);
        assertEquals(accepted ? Kind.TRUE : Kind.FALSE, entry.onlineAccount);
        assertEquals(accepted ? AccountState.BOUND : AccountState.BIND_PENDING, bridge.accountStatus(NAME).state());
        if (accepted) {
            verify(auth).easyAuth$restoreTrueLocation();
        } else {
            verify(auth, never()).easyAuth$restoreTrueLocation();
            verify(auth, never()).easyAuth$setAuthenticated(true);
        }
    }

    private static void set(EasyAuth344Bridge bridge, String name, Object value) throws Exception {
        Field field = EasyAuth344Bridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    public interface PlayerAuth {
        boolean easyAuth$isAuthenticated();

        Entry easyAuth$getPlayerEntryV1();

        String easyAuth$getIpAddress();

        void easyAuth$setAuthenticated(boolean authenticated);

        void easyAuth$restoreTrueLocation();
    }

    public static final class EasyAuth {
        public static Database DB;
        public static final Config config = new Config();
    }

    public static final class Config {
        public long resetLoginAttemptsTimeout = 30;
    }

    public static final class IpLimit {
        public static void invalidateCache(String ip) {
        }
    }

    public static final class Database {
        final Entry entry = new Entry();
        int writes;

        public Entry getUserData(String name) {
            return NAME.equals(name) ? entry : null;
        }

        public boolean updateUserData(Entry candidate) {
            assertEquals(entry, candidate);
            writes++;
            return true;
        }
    }

    public enum Kind {
        FALSE, TRUE
    }

    public static final class Entry {
        public String username = NAME;
        public String password = "old-hash";
        public UUID uuid = OFFLINE;
        public Kind onlineAccount = Kind.FALSE;
        public ZonedDateTime lastKickedDate = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        public ZonedDateTime lastAuthenticatedDate = lastKickedDate;
        public String lastIp = "127.0.0.1";
        public long loginTries = 2;
    }
}
