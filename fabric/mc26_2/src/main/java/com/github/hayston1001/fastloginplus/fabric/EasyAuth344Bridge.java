/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Decision;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.network.chat.Component;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Consumer;

/** EasyAuth 3.4.x bridge using the account and login API verified against 3.4.4. */
final class EasyAuth344Bridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    private static final String EASY_AUTH = "xyz.nikitacartes.easyauth.EasyAuth";
    private static final String PLAYER_AUTH = "xyz.nikitacartes.easyauth.interfaces.PlayerAuth";
    private static final String ENTRY = "xyz.nikitacartes.easyauth.storage.PlayerEntryV1";
    private static final String IP_LIMIT = "xyz.nikitacartes.easyauth.utils.IpLimitManager";
    private static final String PLAYERS_CACHE = "xyz.nikitacartes.easyauth.utils.PlayersCache";
    private static final String EASY_AUTH_SHA256 =
            "1945c37f98a60df5aa33c18261413b523aa949585a0e3d7b870d3cfa02e64f62";
    private static final String[] RENAME_FIELDS = {
        "password", "uuid", "lastIp", "lastAuthenticatedDate", "loginTries", "lastKickedDate",
        "onlineAccount", "registrationDate", "dataVersion", "forcedUuid"
    };

    private final Class<?> easyAuth;
    private final Class<?> playerAuth;
    private final Class<?> entryType;
    private final Class<?> ipLimit;
    private final Class<?> playersCache;
    private final ConcurrentHashMap<String, PendingBinding> pendingBindings = new ConcurrentHashMap<>();
    private static final long BIND_WINDOW_NANOS = TimeUnit.MINUTES.toNanos(10);
    private final PremiumNameResolver premiumNames = PremiumNameResolver.mojang();

    EasyAuth344Bridge() {
        var mod = FabricLoader.getInstance().getModContainer("easyauth")
                .orElseThrow(() -> new IllegalStateException("EasyAuth 3.4.x is required"));
        String version = mod.getMetadata().getVersion().getFriendlyString();
        if (!EasyAuthVersionPolicy.supports(version)) {
            throw new IllegalStateException("Unsupported EasyAuth version: " + version);
        }
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
            throw new IllegalStateException("EasyAuth " + version + " API member is unavailable: "
                    + missingMember.getMessage(), missingMember);
        }
        warnReleaseJar(mod, LOGGER);
    }

    static void warnReleaseJar(ModContainer mod, Logger logger) {
        String actual = null;
        try {
            ModOrigin origin = mod.getOrigin();
            if (origin.getKind() == ModOrigin.Kind.PATH && origin.getPaths().size() == 1) {
                Path jar = origin.getPaths().get(0);
                if (Files.isRegularFile(jar) && jar.getFileName().toString().endsWith(".jar")) {
                    actual = fingerprint(jar);
                }
            }
        } catch (IOException | NoSuchAlgorithmException | RuntimeException unavailable) {
            // A missing or unreadable fingerprint is advisory, including development origins.
        }
        EasyAuthVersionPolicy.fingerprintWarning(EASY_AUTH_SHA256, actual).ifPresent(logger::warn);
    }

    private static String fingerprint(Path jar) throws IOException, NoSuchAlgorithmException {
        try (InputStream stream = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = stream.read(buffer)) != -1;) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
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
                    followRename(entry, claim.getPlayerName(), claim.getVerifiedMojangUuid());
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

    private RenameFollow.Row renameRow(Object entry) {
        return new RenameFollow.Row((String) field(entryType, entry, "username"),
                (String) field(entryType, entry, "password"),
                Objects.toString(field(entryType, entry, "onlineAccount")),
                (UUID) field(entryType, entry, "uuid"));
    }

    private void followRename(Object entry, String name, UUID verifiedUuid) {
        RenameFollow.Row current = renameRow(entry);
        if (!name.equals(current.name())) {
            throw FabricMessages.failure("ownership-conflict");
        }
        if (!RenameFollow.needsLookup(current, verifiedUuid)) {
            return;
        }
        Object db = field(easyAuth, null, "DB");
        Map<?, ?> rows = renameCandidates(db, verifiedUuid);
        RenameFollow.Result result = RenameFollow.decide(name, current,
                rows.values().stream().map(this::renameRow).toList(), verifiedUuid);
        if (result.action() == RenameFollow.Action.CONFLICT) {
            LOGGER.warn("Conflicting EasyAuth rename sources for {} ({}): {}", name, verifiedUuid, result.names());
            throw FabricMessages.failure("rename-conflict");
        }
        if (result.action() == RenameFollow.Action.MIGRATE) {
            String oldName = result.names().getFirst();
            migrateRename(db, entry, rows.get(oldName), name, oldName);
        }
    }

    private Map<?, ?> renameCandidates(Object db, UUID verifiedUuid) {
        if (!db.getClass().getName().equals("xyz.nikitacartes.easyauth.storage.database.SQLite")) {
            return (Map<?, ?>) invoke(db, "getAllData");
        }
        SQLiteStorage storage = sqliteStorage(db);
        Map<String, Object> matches = new HashMap<>();
        for (SQLiteAccountReader.Row row : SQLiteAccountReader.findByUuid(
                storage.connection(), storage.table(), verifiedUuid)) {
            matches.put(row.username(), sqliteEntry(row));
        }
        return matches;
    }

    private void migrateRename(Object db, Object entry, Object previous, String name, String oldName) {
        Field[] fields = new Field[RENAME_FIELDS.length];
        Object[] original = new Object[fields.length];
        Object[] replacement = new Object[fields.length];
        boolean copying = false;
        Method put;
        try {
            // Resolve before touching the live cache or saving the new row; older 3.4.x APIs may lack put.
            put = playersCache.getMethod("put", String.class, entryType);
            for (int i = 0; i < fields.length; i++) {
                fields[i] = entryType.getField(RENAME_FIELDS[i]);
                original[i] = fields[i].get(entry);
                replacement[i] = fields[i].get(previous);
            }
            copying = true;
            for (int i = 0; i < fields.length; i++) {
                fields[i].set(entry, replacement[i]);
            }
            if (!Boolean.TRUE.equals(invoke(db, "updateUserData", entryType, entry))) {
                throw new IllegalStateException("EasyAuth did not save the renamed account");
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (copying) {
                for (int i = 0; i < fields.length; i++) {
                    try {
                        fields[i].set(entry, original[i]);
                    } catch (IllegalAccessException | RuntimeException restoreFailure) {
                        failure.addSuppressed(restoreFailure);
                    }
                }
            }
            LOGGER.error("Could not migrate EasyAuth account {} to {}", oldName, name, failure);
            throw FabricMessages.failure("rename-failed", failure);
        }
        try {
            put.invoke(null, name, entry);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // The new row is durable. Keep the old row too and refuse this login so a retry can re-read it.
            LOGGER.error("EasyAuth account {} was saved, but its renamed cache entry is unavailable", name, failure);
            throw FabricMessages.failure("rename-failed", failure);
        }
        try {
            if (!Boolean.TRUE.equals(invoke(db, "deleteUserData", String.class, oldName))) {
                LOGGER.warn("Renamed EasyAuth account to {}, but old row {} was not deleted", name, oldName);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Renamed EasyAuth account to {}, but old row {} could not be deleted", name, oldName, failure);
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
     * The gate also requires the proxy's recorded consent before conversion.
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
            // No automatic conversion: consent or a claim window may be absent,
            // or policy may keep the password/registration flow. Explain the
            // actual branch; password holders can run /flp premium after login.
            LOGGER.info("Premium login not applied; the password lock stays for {}",
                    player.getGameProfile().name());
            player.sendSystemMessage(FabricMessages.text(passwordLockMessage(claim, state)));
            return;
        }
        if (action == PremiumGate.Action.REJECT) {
            throw FabricMessages.failure("ownership-conflict");
        }
        if (action == PremiumGate.Action.CLAIM_AND_LOGIN) {
            // A later verified connection with recorded consent claims the window;
            // the command never converts an already authenticated password account.
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
        if (state.state() == AccountState.OPEN) {
            return "registration-required";
        }
        return claim.getConsent() == IdentityClaim.Consent.YES
                ? "binding-window-unavailable" : "binding-consent-required";
    }

    /**
     * Queues a binding for an authenticated password holder, whether this
     * connection is offline or verified. The command pins the name's premium
     * UUID and sends consent to the proxy, which may require confirmation.
     * The EasyAuth account is untouched until a later verified connection
     * matches the pin and carries the proxy's recorded consent, so an
     * unconfirmed or failed upgrade keeps the password lock.
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
        BindingRequest.requireBindable(claim.getDecision(), authenticated, state.state());
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
        player.sendSystemMessage(FabricMessages.text("binding-checking"));
        // resolve the pinned premium UUID off the server thread, then finish on it
        CompletableFuture.supplyAsync(() -> premiumNames.resolve(name)).whenComplete((target, error) ->
                server.execute(() -> finishBinding(player, name, passwordHash, target, proxyIds)));
    }

    private void finishBinding(ServerPlayer player, String name, String passwordHash,
                               Optional<UUID> target, String proxyIds) {
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
        // Request the upgrade; the proxy may defer it for confirmation.
        // The EasyAuth account stays a password account until a later claim.
        BindingConsentWriter.send(player, name, proxyIds);
        player.sendSystemMessage(FabricMessages.text("binding-requested", "{uuid}", target.get().toString()));
    }

    /**
     * Authorizes another name without password-holder proof. Called on the server
     * thread; only the name lookup runs asynchronously. Conversion still requires
     * a later verified connection, the pinned account and recorded proxy consent.
     *
     * @param server     the running server
     * @param carrier    the player connection relaying consent
     * @param targetName exact name whose premium login is authorized
     * @param actor      command source name for the audit log
     * @param feedback   command-source feedback, invoked on the server thread
     */
    void adminPremium(MinecraftServer server, ServerPlayer carrier, String targetName,
                      String actor, Consumer<Component> feedback) {
        try {
            AccountState.Snapshot state = accountStatus(targetName);
            if (state.state() == AccountState.BOUND) {
                feedback.accept(FabricMessages.text("admin-premium-already", "{player}", targetName));
                return;
            }
            if (state.state() == AccountState.CONFLICT) {
                feedback.accept(FabricMessages.text("ownership-conflict"));
                return;
            }
            FabricConfig.StartupSettings config = FabricConfig.startup();
            FabricConfig.warnIfUntrusted(config);
            String proxyIds = config.allowedProxyIds();
            if (state.state() == AccountState.OPEN) {
                sendAdminConsent(carrier, targetName, proxyIds);
                LOGGER.info("Admin premium authorization: actor={} target={} queued=false", actor, targetName);
                feedback.accept(FabricMessages.text("admin-premium-open", "{player}", targetName));
                return;
            }
            Object current = readAccount(field(easyAuth, null, "DB"), targetName);
            checkBindingEntry(current, targetName);
            String passwordHash = (String) field(entryType, current, "password");
            CompletableFuture.supplyAsync(() -> premiumNames.resolve(targetName)).whenComplete((target, error) ->
                    server.execute(() -> finishAdminPremium(carrier, targetName, actor, passwordHash,
                            target, proxyIds, feedback)));
        } catch (RuntimeException failure) {
            adminPremiumFailure(actor, targetName, feedback, failure);
        }
    }

    private void finishAdminPremium(ServerPlayer carrier, String name, String actor, String passwordHash,
                                    Optional<UUID> target, String proxyIds, Consumer<Component> feedback) {
        try {
            if (target == null || target.isEmpty()) {
                feedback.accept(FabricMessages.text("admin-premium-name-unavailable", "{player}", name));
                return;
            }
            verifyPolicy();
            Object current = readAccount(field(easyAuth, null, "DB"), name);
            try {
                checkBindingEntry(current, name);
            } catch (FabricMessages.PlayerFailure changed) {
                feedback.accept(FabricMessages.text("admin-premium-changed", "{player}", name));
                return;
            }
            if (!passwordHash.equals(field(entryType, current, "password"))) {
                feedback.accept(FabricMessages.text("admin-premium-changed", "{player}", name));
                return;
            }
            PendingBinding pending = new PendingBinding(offlineUuid(name), passwordHash, target.get());
            PendingBinding previous = pendingBindings.put(name, pending);
            try {
                sendAdminConsent(carrier, name, proxyIds);
            } catch (RuntimeException failure) {
                if (previous == null) {
                    pendingBindings.remove(name, pending);
                } else {
                    pendingBindings.replace(name, pending, previous);
                }
                throw failure;
            }
            LOGGER.info("Admin premium authorization: actor={} target={} queued=true uuid={}", actor, name,
                    target.get());
            feedback.accept(FabricMessages.text("admin-premium-queued", "{player}", name,
                    "{uuid}", target.get().toString()));
        } catch (RuntimeException failure) {
            adminPremiumFailure(actor, name, feedback, failure);
        }
    }

    private static void sendAdminConsent(ServerPlayer carrier, String name, String proxyIds) {
        if (!carrier.connection.isAcceptingMessages()) {
            throw new IllegalStateException("Admin premium message carrier disconnected");
        }
        BindingConsentWriter.sendForOther(carrier, name, proxyIds);
    }

    private static void adminPremiumFailure(String actor, String name, Consumer<Component> feedback,
                                            RuntimeException failure) {
        LOGGER.error("Admin premium authorization failed: actor={} target={}", actor, name, failure);
        feedback.accept(FabricMessages.text("auth-unavailable"));
    }

    /**
     * Claims the pending binding on a Mojang-verified connection with recorded
     * proxy consent. The pin is
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
     * UUID of the claimed name, and the claim window. A verified connection
     * whose UUID matches the pin and whose consent is recorded can claim it.
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
        SQLiteStorage storage = sqliteStorage(db);
        Object extended = field(easyAuth, null, "extendedConfig");
        boolean caseInsensitive = bool(extended, "allowCaseInsensitiveUsername");
        SQLiteAccountReader.Row row = SQLiteAccountReader.read(
                storage.connection(), storage.table(), name, caseInsensitive);
        return row == null ? null : sqliteEntry(row);
    }

    private static SQLiteStorage sqliteStorage(Object db) {
        try {
            Field connectionField = db.getClass().getDeclaredField("connection");
            connectionField.setAccessible(true);
            Field configField = db.getClass().getDeclaredField("config");
            configField.setAccessible(true);
            Object storageConfig = configField.get(db);
            Object sqliteConfig = field(storageConfig.getClass(), storageConfig, "sqlite");
            String table = (String) field(sqliteConfig.getClass(), sqliteConfig, "sqliteTable");
            return new SQLiteStorage((Connection) connectionField.get(db), table);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth SQLite storage is unavailable", error);
        }
    }

    private Object sqliteEntry(SQLiteAccountReader.Row row) {
        try {
            Constructor<?> constructor = entryType.getConstructor(
                    String.class, String.class, String.class, String.class);
            return constructor.newInstance(row.username(), row.usernameLowerCase(), row.uuid(), row.data());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth SQLite account read is unavailable", error);
        }
    }

    private record SQLiteStorage(Connection connection, String table) {
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
