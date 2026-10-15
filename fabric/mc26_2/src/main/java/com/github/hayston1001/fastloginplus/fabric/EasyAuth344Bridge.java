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
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

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
    private final ConcurrentHashMap<String, PendingBinding> pendingBindings = new ConcurrentHashMap<>();
    private static final long BIND_WINDOW_NANOS = TimeUnit.MINUTES.toNanos(10);
    /** The proxy needs a moment to record the consent before the reconnect asks for verification. */
    private static final long CONSENT_SETTLE_DELAY_MILLIS = 1000;
    private final PremiumNameResolver premiumNames = PremiumNameResolver.mojang();

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
            Class<?> dbType = Class.forName("xyz.nikitacartes.easyauth.storage.database.DbApi");
            dbType.getMethod("getUserData", String.class);
            entryType.getField("password");
            entryType.getField("uuid");
            entryType.getField("onlineAccount");
            entryType.getField("lastKickedDate");
            entryType.getField("lastAuthenticatedDate");
            entryType.getField("loginTries");
            entryType.getField("lastIp");
            entryType.getField("username");
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
                AccountState.Snapshot state = accountStatus(claim.getPlayerName());
                PendingBinding binding = matchedBinding(claim);
                if (claim.getDecision() == Decision.PREMIUM_VERIFIED) {
                    // R-10: the declared policy governs; an older proxy that
                    // declared nothing keeps the previous behaviour (on) — the
                    // Fabric config has no local copy of this key (F-15 A)
                    boolean autoRegisterUnknown = claim.getFacts().policyDeclared()
                            ? claim.getFacts().autoRegisterUnknown() : true;
                    PremiumGate.Action action = PremiumGate.decideVerified(claim.getConsent(), state,
                            claim.getVerifiedMojangUuid(), binding != null, claim.isAutoLogin(),
                            autoRegisterUnknown);
                    if (action == PremiumGate.Action.REJECT) {
                        throw FabricMessages.failure("ownership-conflict");
                    }
                    if (action == PremiumGate.Action.PASSWORD_LOCK) {
                        // No player exists yet. onJoin rechecks the current account
                        // and delivers passwordLockMessage exactly once in play.
                        return;
                    }
                    if (action == PremiumGate.Action.CLAIM_AND_LOGIN) {
                        checkBindingEntry(entry, claim.getPlayerName());
                    } else {
                        checkPremiumEntry(entry, claim.getPlayerName(), claim.getVerifiedMojangUuid());
                    }
                } else {
                    if (PremiumGate.decideUnverified(state) == PremiumGate.Action.REJECT) {
                        throw FabricMessages.failure("ownership-conflict");
                    }
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
        if (bool(config, "offlineByDefault")) {
            throw new IllegalStateException("EasyAuth offline-by-default must be false for first premium login");
        }
        if (bool(config, "floodgateAutoLogin")) {
            throw new IllegalStateException("EasyAuth floodgate-auto-login must be false");
        }
        if (bool(config, "enableGlobalPassword") && !bool(config, "singleUseGlobalPassword")) {
            throw new IllegalStateException("EasyAuth reusable global password cannot prove account ownership");
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

    AccountState.Snapshot accountStatus(String name) {
        verifyPolicy();
        Object db = field(easyAuth, null, "DB");
        Object entry = readAccount(db, name);
        if (entry == null) {
            return new AccountState.Snapshot(AccountState.OPEN, null);
        }
        if (!entryType.isInstance(entry) || !name.equals(field(entryType, entry, "username"))) {
            return new AccountState.Snapshot(AccountState.CONFLICT, null);
        }
        String password = (String) field(entryType, entry, "password");
        if (password == null) {
            return new AccountState.Snapshot(AccountState.CONFLICT, null);
        }
        if (!password.isEmpty()) {
            UUID accountUuid = (UUID) field(entryType, entry, "uuid");
            String kind = Objects.toString(field(entryType, entry, "onlineAccount"));
            if (!offlineUuid(name).equals(accountUuid)
                    || (!"FALSE".equals(kind) && !"UNKNOWN".equals(kind))) {
                return new AccountState.Snapshot(AccountState.CONFLICT, null);
            }
            PendingBinding pending = pendingBindings.get(name);
            if (pending != null && !pending.matchesAccount(accountUuid, password)) {
                pendingBindings.remove(name, pending);
                pending = null;
            }
            if (pending != null) {
                return new AccountState.Snapshot(AccountState.BIND_PENDING, null);
            }
            return new AccountState.Snapshot(AccountState.PASSWORD, null);
        }
        String kind = Objects.toString(field(entryType, entry, "onlineAccount"));
        UUID uuid = (UUID) field(entryType, entry, "uuid");
        if ("TRUE".equals(kind) && uuid != null) {
            return new AccountState.Snapshot(AccountState.BOUND, uuid);
        }
        if ("UNKNOWN".equals(kind)) {
            return new AccountState.Snapshot(AccountState.OPEN, null);
        }
        return new AccountState.Snapshot(AccountState.CONFLICT, null);
    }

    /**
     * Returns the pending binding this verified identity may claim: a live
     * entry for the name whose pinned premium UUID matches the verified one.
     * The verification is the claim — a matching verified connection is the
     * only proof the binding window accepts.
     *
     * @param claim the identity facts of the connecting player
     * @return the claimable entry, or null when nothing may be claimed
     */
    private PendingBinding matchedBinding(IdentityClaim claim) {
        PendingBinding pending = pendingBindings.get(claim.getPlayerName());
        if (pending == null || !pending.matchesTarget(claim.getVerifiedMojangUuid())) {
            return null;
        }
        return pending;
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
        AccountState.Snapshot state = accountStatus(claim.getPlayerName());
        PendingBinding binding = matchedBinding(claim);
        if (claim.getDecision() == Decision.OFFLINE) {
            if (PremiumGate.decideUnverified(state) == PremiumGate.Action.REJECT) {
                throw FabricMessages.failure("ownership-conflict");
            }
            checkOfflineEntry(entry, claim.getPlayerName(), claim.getForwardedUuid());
            LOGGER.info("EasyAuth password lock remains active for {}", player.getGameProfile().name());
            return;
        }
        if (claim.getDecision() != Decision.PREMIUM_VERIFIED) {
            throw new IllegalStateException("Unresolved identity claim");
        }
        PremiumGate.Action action = PremiumGate.decideVerified(claim.getConsent(), state,
                claim.getVerifiedMojangUuid(), binding != null, claim.isAutoLogin(),
                !claim.getFacts().policyDeclared() || claim.getFacts().autoRegisterUnknown());
        if (action == PremiumGate.Action.PASSWORD_LOCK) {
            // no premium handling to do: the claim window expired, the entry was
            // lost to a restart, the upgrade never finished, or the autoLogin
            // policy is off. The password account keeps its password and
            // /flp premium can be retried after logging in.
            LOGGER.info("Premium login not applied; the password lock stays for {}",
                    player.getGameProfile().name());
            player.sendSystemMessage(FabricMessages.text(passwordLockMessage(claim, state)));
            return;
        }
        if (action == PremiumGate.Action.REJECT) {
            throw FabricMessages.failure("ownership-conflict");
        }
        if (action == PremiumGate.Action.CLAIM_AND_LOGIN) {
            // the first successful passwordless login claims the queued binding:
            // offline starters convert here; already verified password holders
            // can also convert explicitly with /flp premium after authentication
            checkBindingEntry(entry, claim.getPlayerName());
            completeBindingClaim(player, claim, entry, binding);
            return;
        }
        authenticatePremium(player, entry, claim.getVerifiedMojangUuid());
    }

    static String passwordLockMessage(IdentityClaim claim, AccountState.Snapshot state) {
        if (!claim.isAutoLogin()) {
            return "premium-policy-disabled";
        }
        return state.state() == AccountState.OPEN ? "registration-required" : "binding-window-unavailable";
    }

    private void completeAuthenticatedBinding(ServerPlayer player, IdentityClaim claim,
                                              Object entry, Object current) {
        String name = claim.getPlayerName();
        verifyPolicy();
        BindingRequest.completeNow(claim.getDecision(),
                Boolean.TRUE.equals(invoke(player, "easyAuth$isAuthenticated")), accountStatus(name).state());
        checkBindingEntry(entry, name);
        checkBindingEntry(current, name);
        if (!Objects.equals(field(entryType, entry, "password"), field(entryType, current, "password"))) {
            throw FabricMessages.failure("binding-account-changed");
        }
        Object db = field(easyAuth, null, "DB");
        // Already authenticated: persist the conversion without replaying EasyAuth's
        // login completion or restoring its pre-login location a second time.
        try {
            EasyAuthAccountUpdate.bind(entryType, entry, claim.getVerifiedMojangUuid(),
                    candidate -> Boolean.TRUE.equals(invoke(db, "updateUserData", entryType, candidate)));
        } catch (RuntimeException failure) {
            throw FabricMessages.failure("binding-save-failed", failure);
        }
        pendingBindings.remove(name);
        LOGGER.info("Bound authenticated EasyAuth password account {} to verified Mojang UUID {}",
                name, claim.getVerifiedMojangUuid());
    }

    /**
     * Binds a verified, authenticated password holder immediately, or queues
     * verification for an offline password holder. The offline command
     * only sets the proxy's FastLoginPlus record (the network-level preference)
     * and this entry (pin of the claimed name's premium UUID, one claim window);
     * the EasyAuth account is <em>not</em> touched. The account is converted to
     * premium (cleared password, pinned UUID) only at the first successful
     * passwordless login whose verified UUID matches the pin, so a failed
     * upgrade never costs the password lock.
     *
     * @param server the running server
     * @param player the authenticated password account holder
     * @param claim  the identity facts of the current connection
     */
    void beginBinding(MinecraftServer server, ServerPlayer player, IdentityClaim claim) {
        verifyPolicy();
        String name = player.getGameProfile().name();
        boolean authenticated = Boolean.TRUE.equals(invoke(player, "easyAuth$isAuthenticated"));
        if (!authenticated) {
            throw FabricMessages.failure("binding-login-required");
        }
        Object entry = invoke(player, "easyAuth$getPlayerEntryV1");
        AccountState.Snapshot state = accountStatus(name);
        boolean completeNow = BindingRequest.completeNow(claim.getDecision(), authenticated, state.state());
        checkBindingEntry(entry, name);
        FabricConfig.StartupSettings config = FabricConfig.startup();
        String proxyIds = config.allowedProxyIds();
        FabricConfig.warnIfUntrusted(config);
        Object db = field(easyAuth, null, "DB");
        Object current = readAccount(db, name);
        checkBindingEntry(current, name);
        String passwordHash = (String) field(entryType, entry, "password");
        if (!passwordHash.equals(field(entryType, current, "password"))) {
            throw FabricMessages.failure("binding-account-saving");
        }
        if (completeNow) {
            completeAuthenticatedBinding(player, claim, entry, current);
            BindingConsentWriter.send(player, name, proxyIds);
            player.sendSystemMessage(FabricMessages.text("binding-complete"));
            return;
        }
        TransferTarget transfer = transferTarget(FabricConfig.readTransferSettings());
        player.sendSystemMessage(FabricMessages.text("binding-checking"));
        // resolve the pinned premium UUID off the server thread, then finish on it
        CompletableFuture.supplyAsync(() -> premiumNames.resolve(name)).whenComplete((target, error) ->
                server.execute(() -> finishBinding(server, player, name, passwordHash, target,
                        transfer, proxyIds)));
    }

    private void finishBinding(MinecraftServer server, ServerPlayer player, String name,
                               String passwordHash, Optional<UUID> target, TransferTarget transfer,
                               String proxyIds) {
        if (target == null || target.isEmpty()) {
            player.sendSystemMessage(FabricMessages.text("binding-name-unavailable"));
            return;
        }
        if (!player.connection.isAcceptingMessages()) {
            return;
        }
        Object db = field(easyAuth, null, "DB");
        Object current = readAccount(db, name);
        try {
            checkBindingEntry(current, name);
        } catch (RuntimeException changed) {
            player.sendSystemMessage(FabricMessages.text("binding-account-changed"));
            return;
        }
        if (!passwordHash.equals(field(entryType, current, "password"))) {
            player.sendSystemMessage(FabricMessages.text("binding-account-changed"));
            return;
        }
        PendingBinding pending = new PendingBinding(offlineUuid(name), passwordHash, target.get());
        pendingBindings.put(name, pending);
        // the command only books the upgrade: the proxy record (network-level
        // preference) is set here, the EasyAuth account stays a password account
        BindingConsentWriter.send(player, name, proxyIds);
        player.sendSystemMessage(FabricMessages.text("binding-queued", "{uuid}", target.get().toString()));
        CompletableFuture.delayedExecutor(CONSENT_SETTLE_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() ->
                server.execute(() -> transferForBinding(player, name, pending, transfer.host, transfer.port)));
    }

    private void transferForBinding(ServerPlayer player, String name, PendingBinding pending,
                                    String host, int port) {
        if (pendingBindings.get(name) == pending && pending.isLive()
                && player.connection.isAcceptingMessages()) {
            LOGGER.info("Transferring {} for verified premium binding", name);
            player.connection.send(new ClientboundTransferPacket(host, port));
        }
    }

    private TransferTarget transferTarget(FabricConfig.TransferSettings config) {
        String host = config.host().trim();
        int port;
        try {
            port = Integer.parseInt(config.port());
        } catch (NumberFormatException error) {
            throw FabricMessages.failure("binding-transfer-unavailable");
        }
        if (host.isEmpty() || port < 1 || port > 65535) {
            throw FabricMessages.failure("binding-transfer-unavailable");
        }
        return new TransferTarget(host, port);
    }

    private record TransferTarget(String host, int port) {
    }

    /**
     * Claims the pending binding on a Mojang-verified connection. The pin is
     * already matched (see {@link #matchedBinding}); the account must still be
     * the one the holder started the binding with, otherwise the entry dies
     * and the password account stays untouched.
     *
     * @param player  the verified connection claiming the account
     * @param claim   the identity facts of that connection
     * @param entry   the live EasyAuth account entry
     * @param pending the queued binding being claimed
     */
    private void completeBindingClaim(ServerPlayer player, IdentityClaim claim, Object entry,
                                      PendingBinding pending) {
        String name = claim.getPlayerName();
        if (!pending.matchesAccount((UUID) field(entryType, entry, "uuid"),
                (String) field(entryType, entry, "password"))) {
            player.sendSystemMessage(FabricMessages.text("binding-account-changed"));
            LOGGER.warn("Binding window no longer matches the EasyAuth account for {}", name);
            return;
        }
        Object db = field(easyAuth, null, "DB");
        Object current = readAccount(db, name);
        checkBindingEntry(current, name);
        if (!pending.matchesAccount((UUID) field(entryType, current, "uuid"),
                (String) field(entryType, current, "password"))) {
            throw FabricMessages.failure("binding-account-changed");
        }
        try {
            authenticatePremium(player, entry, claim.getVerifiedMojangUuid(), true);
        } catch (RuntimeException failure) {
            AccountState.Snapshot saved = accountStatus(name);
            if (saved.state() == AccountState.BOUND
                    && claim.getVerifiedMojangUuid().equals(saved.uuid())) {
                pendingBindings.remove(name, pending);
                LOGGER.error("Binding was saved for {}, but EasyAuth could not finish this login", name, failure);
                player.connection.disconnect(FabricMessages.text("binding-saved-reconnect"));
                return;
            }
            throw failure;
        }
        pendingBindings.remove(name, pending);
        LOGGER.info("Bound EasyAuth password account {} to verified Mojang UUID {}", name,
                claim.getVerifiedMojangUuid());
    }

    private void checkBindingEntry(Object entry, String name) {
        if (entry == null || !entryType.isInstance(entry)
                || !name.equals(field(entryType, entry, "username"))
                || !offlineUuid(name).equals(field(entryType, entry, "uuid"))
                || !(field(entryType, entry, "password") instanceof String password) || password.isEmpty()
                || (!"FALSE".equals(Objects.toString(field(entryType, entry, "onlineAccount")))
                && !"UNKNOWN".equals(Objects.toString(field(entryType, entry, "onlineAccount"))))) {
            throw FabricMessages.failure("binding-account-changed");
        }
    }

    /**
     * One queued binding: the account it was started from, the pinned premium
     * UUID of the claimed name, and the claim window. One verified connection
     * whose UUID matches the pin claims it.
     */
    static final class PendingBinding {
        private final UUID offlineUuid;
        private final String passwordHash;
        private final UUID targetUuid;
        private final long expiresAt;
        private final LongSupplier nanoTime;

        private PendingBinding(UUID offlineUuid, String passwordHash, UUID targetUuid) {
            this(offlineUuid, passwordHash, targetUuid, System::nanoTime);
        }

        PendingBinding(UUID offlineUuid, String passwordHash, UUID targetUuid, LongSupplier nanoTime) {
            this.offlineUuid = offlineUuid;
            this.passwordHash = passwordHash;
            this.targetUuid = targetUuid;
            this.nanoTime = nanoTime;
            this.expiresAt = nanoTime.getAsLong() + BIND_WINDOW_NANOS;
        }

        boolean isLive() {
            return nanoTime.getAsLong() - expiresAt < 0;
        }

        /**
         * The EasyAuth account is still the one the binding was started from.
         *
         * @param accountUuid         the account's stored UUID
         * @param currentPasswordHash the account's current password hash
         * @return true when the window is live and the account is unchanged
         */
        boolean matchesAccount(UUID accountUuid, String currentPasswordHash) {
            return isLive() && offlineUuid.equals(accountUuid)
                    && passwordHash.equals(currentPasswordHash);
        }

        /**
         * The verified identity is the pinned premium identity of the name.
         *
         * @param verifiedUuid the Mojang UUID the connection was verified as
         * @return true when the window is live and the UUID matches the pin
         */
        boolean matchesTarget(UUID verifiedUuid) {
            return isLive() && targetUuid.equals(verifiedUuid);
        }
    }

    private void authenticatePremium(ServerPlayer player, Object entry, UUID verifiedUuid) {
        authenticatePremium(player, entry, verifiedUuid, false);
    }

    private void authenticatePremium(ServerPlayer player, Object entry, UUID verifiedUuid, boolean binding) {
        if (binding) {
            checkBindingEntry(entry, player.getGameProfile().name());
        } else {
            checkPremiumEntry(entry, player.getGameProfile().name(), verifiedUuid);
        }
        Object config = field(easyAuth, null, "config");
        ZonedDateTime lastKick = (ZonedDateTime) field(entryType, entry, "lastKickedDate");
        String ip = (String) invoke(player, "easyAuth$getIpAddress");
        String oldIp = (String) field(entryType, entry, "lastIp");

        Object db = field(easyAuth, null, "DB");
        var context = new EasyAuthLoginFlow.LoginContext(ZonedDateTime.now(), lastKick,
                number(config, "resetLoginAttemptsTimeout"), ip, oldIp);
        EasyAuthLoginFlow.authenticate(entryType, entry, verifiedUuid, context, binding,
                candidate -> Boolean.TRUE.equals(invoke(db, "updateUserData", entryType, candidate)), () -> {
                    invoke(ipLimit, null, "invalidateCache", String.class, oldIp);
                    if (!oldIp.equals(ip)) {
                        invoke(ipLimit, null, "invalidateCache", String.class, ip);
                    }
                    invoke(player, "easyAuth$restoreTrueLocation");
                    invoke(player, "easyAuth$setAuthenticated", boolean.class, true);
                    if (!Boolean.TRUE.equals(invoke(player, "easyAuth$isAuthenticated"))) {
                        throw new IllegalStateException("EasyAuth did not accept the premium authentication state");
                    }
                    player.sendSystemMessage(FabricMessages.text("authenticated"));
                    LOGGER.info("Completed EasyAuth premium login for {}", player.getGameProfile().name());
                });
    }

    private void checkPremiumEntry(Object entry, String name, UUID verifiedUuid) {
        String accountName = (String) field(entryType, entry, "username");
        String password = (String) field(entryType, entry, "password");
        UUID accountUuid = (UUID) field(entryType, entry, "uuid");
        String accountKind = Objects.toString(field(entryType, entry, "onlineAccount"));
        boolean unclaimed = "UNKNOWN".equals(accountKind)
                && (accountUuid == null || accountUuid.equals(offlineUuid(name))
                || accountUuid.equals(verifiedUuid));
        boolean bound = "TRUE".equals(accountKind) && verifiedUuid.equals(accountUuid);
        if (!name.equals(accountName) || password == null || !password.isEmpty() || (!unclaimed && !bound)) {
            throw FabricMessages.failure("ownership-conflict");
        }
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private Object readAccount(Object db, String name) {
        if (!db.getClass().getName().equals("xyz.nikitacartes.easyauth.storage.database.SQLite")) {
            return invoke(db, "getUserData", String.class, name);
        }
        try {
            Field connectionField = db.getClass().getDeclaredField("connection");
            connectionField.setAccessible(true);
            Field configField = db.getClass().getDeclaredField("config");
            configField.setAccessible(true);
            Object storageConfig = configField.get(db);
            Object sqliteConfig = field(storageConfig.getClass(), storageConfig, "sqlite");
            String table = (String) field(sqliteConfig.getClass(), sqliteConfig, "sqliteTable");
            Object extended = field(easyAuth, null, "extendedConfig");
            boolean caseInsensitive = bool(extended, "allowCaseInsensitiveUsername");
            SQLiteAccountReader.Row row = SQLiteAccountReader.read(
                    (Connection) connectionField.get(db), table, name, caseInsensitive);
            if (row == null) {
                return null;
            }
            Constructor<?> constructor = entryType.getConstructor(
                    String.class, String.class, String.class, String.class);
            return constructor.newInstance(row.username(), row.usernameLowerCase(), row.uuid(), row.data());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth SQLite account read is unavailable", error);
        }
    }

    private void checkOfflineEntry(Object entry, String name, UUID forwardedUuid) {
        String accountName = (String) field(entryType, entry, "username");
        UUID accountUuid = (UUID) field(entryType, entry, "uuid");
        String accountKind = Objects.toString(field(entryType, entry, "onlineAccount"));
        if (!name.equals(accountName) || "TRUE".equals(accountKind)
                || (!"UNKNOWN".equals(accountKind) && !"FALSE".equals(accountKind))
                || (accountUuid != null && !accountUuid.equals(forwardedUuid))) {
            throw FabricMessages.failure("ownership-conflict");
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
