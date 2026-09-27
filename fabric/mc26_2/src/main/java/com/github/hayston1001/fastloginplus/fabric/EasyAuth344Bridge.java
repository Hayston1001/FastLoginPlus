/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Decision;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Fixed-version bridge for EasyAuth 3.4.4's account and login lifecycle. */
final class EasyAuth344Bridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    private static final String EASY_AUTH = "xyz.nikitacartes.easyauth.EasyAuth";
    private static final String PLAYER_AUTH = "xyz.nikitacartes.easyauth.interfaces.PlayerAuth";
    private static final String ENTRY = "xyz.nikitacartes.easyauth.storage.PlayerEntryV1";
    private static final String IP_LIMIT = "xyz.nikitacartes.easyauth.utils.IpLimitManager";
    private static final String PLAYERS_CACHE = "xyz.nikitacartes.easyauth.utils.PlayersCache";
    private static final String EASY_AUTH_SHA256 =
            "1945c37f98a60df5aa33c18261413b523aa949585a0e3d7b870d3cfa02e64f62";

    private final Class<?> easyAuth;
    private final Class<?> playerAuth;
    private final Class<?> entryType;
    private final Class<?> ipLimit;
    private final Class<?> playersCache;

    EasyAuth344Bridge() {
        var mod = FabricLoader.getInstance().getModContainer("easyauth")
                .orElseThrow(() -> new IllegalStateException("EasyAuth 3.4.4 is required"));
        String version = mod.getMetadata().getVersion().getFriendlyString();
        if (!"3.4.4".equals(version)) {
            throw new IllegalStateException("Unsupported EasyAuth version: " + version);
        }
        verifyReleaseJar(mod.getOrigin());
        try {
            easyAuth = Class.forName(EASY_AUTH);
            playerAuth = Class.forName(PLAYER_AUTH);
            entryType = Class.forName(ENTRY);
            ipLimit = Class.forName(IP_LIMIT);
            playersCache = Class.forName(PLAYERS_CACHE);
            // Resolve every internal member before accepting a connection.
            playerAuth.getMethod("easyAuth$isAuthenticated");
            playerAuth.getMethod("easyAuth$setAuthenticated", boolean.class);
            playerAuth.getMethod("easyAuth$restoreTrueLocation");
            playerAuth.getMethod("easyAuth$getPlayerEntryV1");
            playerAuth.getMethod("easyAuth$getIpAddress");
            ipLimit.getMethod("invalidateCache", String.class);
            playersCache.getMethod("get", String.class);
            entryType.getField("password");
            entryType.getField("uuid");
            entryType.getField("onlineAccount");
            entryType.getField("lastKickedDate");
            entryType.getField("lastAuthenticatedDate");
            entryType.getField("loginTries");
            entryType.getField("lastIp");
        } catch (ReflectiveOperationException missingMember) {
            throw new IllegalStateException("EasyAuth 3.4.4 API does not match the verified release", missingMember);
        }
    }

    private static void verifyReleaseJar(ModOrigin origin) {
        if (origin.getKind() != ModOrigin.Kind.PATH || origin.getPaths().size() != 1) {
            throw new IllegalStateException("EasyAuth must be the verified standalone 26.2 release JAR");
        }
        Path jar = origin.getPaths().get(0);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("EasyAuth release JAR path is unavailable");
        }
        try (InputStream stream = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = stream.read(buffer)) != -1;) {
                digest.update(buffer, 0, read);
            }
            if (!EASY_AUTH_SHA256.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IllegalStateException("EasyAuth JAR differs from the verified 26.2 release");
            }
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Could not verify the EasyAuth 26.2 release JAR", error);
        }
    }

    void beforeConfigure(MinecraftServer server, IdentityClaim claim) {
        verifyPolicy();
        try {
            server.submit(() -> {
                Object entry = invoke(playersCache, null, "get", String.class, claim.getPlayerName());
                if (entry == null || !entryType.isInstance(entry)) {
                    throw new IllegalStateException("EasyAuth account was not loaded before configuration");
                }
                if (claim.getDecision() == Decision.PREMIUM_VERIFIED) {
                    checkPremiumEntry(entry, claim.getPlayerName(), claim.getVerifiedMojangUuid());
                } else if (claim.getDecision() == Decision.OFFLINE) {
                    checkOfflineEntry(entry, claim.getPlayerName(), claim.getForwardedUuid());
                }
            }).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EasyAuth account check was interrupted", error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IllegalStateException("EasyAuth account check failed before configuration", error);
        }
    }

    void verifyPolicy() {
        Object config = field(easyAuth, null, "config");
        Object extended = field(easyAuth, null, "extendedConfig");
        Object db = field(easyAuth, null, "DB");
        if (config == null || extended == null || db == null) {
            throw new IllegalStateException("EasyAuth config or database is unavailable");
        }
        if (bool(config, "premiumAutoLogin")) {
            throw new IllegalStateException("EasyAuth premium-auto-login must be false");
        }
        if (bool(config, "floodgateAutoLogin")) {
            throw new IllegalStateException("EasyAuth floodgate-auto-login must be false");
        }
        if (number(config, "sessionTimeout") != -1) {
            throw new IllegalStateException("EasyAuth session-timeout must be -1");
        }
        if (bool(extended, "skipAllAuthChecks") || bool(extended, "allowAllPackets")) {
            throw new IllegalStateException("EasyAuth authentication bypasses must be false");
        }
        if (Boolean.TRUE.equals(invoke(db, "isClosed"))) {
            throw new IllegalStateException("EasyAuth database is closed");
        }
    }

    void onJoin(ServerPlayer player, IdentityClaim claim) {
        verifyPolicy();
        if (!playerAuth.isInstance(player)) {
            throw new IllegalStateException("EasyAuth did not attach PlayerAuth to the player");
        }
        Object entry = invoke(player, "easyAuth$getPlayerEntryV1");
        if (entry == null || !entryType.isInstance(entry)
                || Boolean.TRUE.equals(invoke(player, "easyAuth$isAuthenticated"))) {
            throw new IllegalStateException("EasyAuth account is missing or was authenticated before FLP");
        }
        if (claim.getDecision() == Decision.OFFLINE) {
            checkOfflineEntry(entry, claim.getPlayerName(), claim.getForwardedUuid());
            LOGGER.info("EasyAuth password lock remains active for {}", player.getGameProfile().name());
            return;
        }
        if (claim.getDecision() != Decision.PREMIUM_VERIFIED) {
            throw new IllegalStateException("Unresolved identity claim");
        }
        authenticatePremium(player, entry, claim.getVerifiedMojangUuid());
    }

    private void authenticatePremium(ServerPlayer player, Object entry, UUID verifiedUuid) {
        checkPremiumEntry(entry, player.getGameProfile().name(), verifiedUuid);
        Object config = field(easyAuth, null, "config");
        ZonedDateTime lastKick = (ZonedDateTime) field(entryType, entry, "lastKickedDate");
        if (lastKick == null || lastKick.plusSeconds(number(config, "resetLoginAttemptsTimeout"))
                .isAfter(ZonedDateTime.now())) {
            throw new IllegalStateException("EasyAuth account is in a login timeout");
        }
        String ip = (String) invoke(player, "easyAuth$getIpAddress");
        String oldIp = (String) field(entryType, entry, "lastIp");
        if (ip == null || ip.isEmpty() || oldIp == null) {
            throw new IllegalStateException("EasyAuth did not resolve a player IP");
        }

        Object oldKind = field(entryType, entry, "onlineAccount");
        Object oldDate = field(entryType, entry, "lastAuthenticatedDate");
        Object oldTries = field(entryType, entry, "loginTries");
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object premium = Enum.valueOf((Class<Enum>) oldKind.getClass(), "TRUE");
            set(entryType, entry, "onlineAccount", premium);
            set(entryType, entry, "lastAuthenticatedDate", ZonedDateTime.now());
            set(entryType, entry, "loginTries", 0L);
            set(entryType, entry, "lastIp", ip);
            Object db = field(easyAuth, null, "DB");
            if (!Boolean.TRUE.equals(invoke(db, "updateUserData", entryType, entry))) {
                throw new IllegalStateException("EasyAuth account update failed");
            }
        } catch (RuntimeException failure) {
            set(entryType, entry, "onlineAccount", oldKind);
            set(entryType, entry, "lastAuthenticatedDate", oldDate);
            set(entryType, entry, "loginTries", oldTries);
            set(entryType, entry, "lastIp", oldIp);
            throw failure;
        }

        invoke(ipLimit, null, "invalidateCache", String.class, oldIp);
        if (!oldIp.equals(ip)) {
            invoke(ipLimit, null, "invalidateCache", String.class, ip);
        }
        invoke(player, "easyAuth$restoreTrueLocation");
        invoke(player, "easyAuth$setAuthenticated", boolean.class, true);
        if (!Boolean.TRUE.equals(invoke(player, "easyAuth$isAuthenticated"))) {
            throw new IllegalStateException("EasyAuth did not accept the premium authentication state");
        }
        player.sendSystemMessage(Component.literal("Authenticated with FastLoginPlus"));
        LOGGER.info("Completed EasyAuth premium login for {}", player.getGameProfile().name());
    }

    private void checkPremiumEntry(Object entry, String name, UUID verifiedUuid) {
        String accountName = (String) field(entryType, entry, "username");
        String password = (String) field(entryType, entry, "password");
        UUID accountUuid = (UUID) field(entryType, entry, "uuid");
        String accountKind = Objects.toString(field(entryType, entry, "onlineAccount"));
        if (!name.equals(accountName) || password == null || !password.isEmpty() || "FALSE".equals(accountKind)
                || (!"UNKNOWN".equals(accountKind) && !"TRUE".equals(accountKind))
                || (accountUuid != null && !accountUuid.equals(verifiedUuid))) {
            throw new IllegalStateException("EasyAuth account conflicts with verified premium identity");
        }
    }

    private void checkOfflineEntry(Object entry, String name, UUID forwardedUuid) {
        String accountName = (String) field(entryType, entry, "username");
        UUID accountUuid = (UUID) field(entryType, entry, "uuid");
        String accountKind = Objects.toString(field(entryType, entry, "onlineAccount"));
        if (!name.equals(accountName) || "TRUE".equals(accountKind)
                || (!"UNKNOWN".equals(accountKind) && !"FALSE".equals(accountKind))
                || (accountUuid != null && !accountUuid.equals(forwardedUuid))) {
            throw new IllegalStateException("EasyAuth account conflicts with offline identity");
        }
    }

    private static boolean bool(Object owner, String name) {
        return Boolean.TRUE.equals(field(owner.getClass(), owner, name));
    }

    private static long number(Object owner, String name) {
        return ((Number) field(owner.getClass(), owner, name)).longValue();
    }

    private static Object field(Class<?> type, Object owner, String name) {
        try {
            return type.getField(name).get(owner);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth field is unavailable: " + name, error);
        }
    }

    private static void set(Class<?> type, Object owner, String name, Object value) {
        try {
            type.getField(name).set(owner, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth field cannot be updated: " + name, error);
        }
    }

    private static Object invoke(Object owner, String name) {
        return invoke(owner.getClass(), owner, name);
    }

    private static Object invoke(Class<?> type, Object owner, String name) {
        return invoke(type, owner, name, null, null);
    }

    private static Object invoke(Object owner, String name, Class<?> parameter, Object argument) {
        return invoke(owner.getClass(), owner, name, parameter, argument);
    }

    private static Object invoke(Class<?> type, Object owner, String name, Class<?> parameter, Object argument) {
        try {
            Method method = parameter == null ? type.getMethod(name) : type.getMethod(name, parameter);
            return parameter == null ? method.invoke(owner) : method.invoke(owner, argument);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("EasyAuth operation failed: " + name, error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth operation is unavailable: " + name, error);
        }
    }
}
