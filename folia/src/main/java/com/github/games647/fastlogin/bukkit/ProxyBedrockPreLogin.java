/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockSession;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgatePreLoginAuth;
import com.github.games647.fastlogin.protocol.BedrockClaim;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.entity.Player;

import java.net.SocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Paper/Folia proxy-first AuthMe bridge. No optional Floodgate API is linked here. */
public final class ProxyBedrockPreLogin {
    private final FastLoginBukkit plugin;
    private final Cache<String, Capture> captures = CacheBuilder.newBuilder().maximumSize(4096)
            .expireAfterWrite(2, TimeUnit.MINUTES).build();
    private final Cache<UUID, Boolean> usedNonces = CacheBuilder.newBuilder()
            .expireAfterWrite(125, TimeUnit.SECONDS).build();
    private final Cache<String, Decision> decisions = CacheBuilder.newBuilder().maximumSize(4096)
            .expireAfterWrite(2, TimeUnit.MINUTES).build();
    private final Cache<Player, Decision> handledPlayers = CacheBuilder.newBuilder().weakKeys()
            .maximumSize(4096).build();

    public ProxyBedrockPreLogin(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    // Capture the live, pre-cache profile. Absence clears every reconnect's old pending state.
    public void capture(String name, UUID uuid, Object profile) {
        captures.invalidate(name);
        decisions.invalidate(name);
        if (!plugin.getBungeeManager().isEnabled()) {
            return;
        }
        try {
            List<String> values = SpigotProfileReader.readProfile(profile, "pre-login").bedrock;
            if (!values.isEmpty()) {
                // Even a malformed declaration must not fall back to local policy or Java auto-auth.
                String wire = values.size() == 1 ? values.get(0) : null;
                captures.put(name, new Capture(uuid, wire, verify(wire, name, uuid)));
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            debug("Could not read early Bedrock facts for {}", name);
        }
    }

    public boolean hasDeclaration(String name, UUID uuid) {
        Capture capture = captures.getIfPresent(name);
        return capture != null && capture.uuid.equals(uuid);
    }

    public boolean hasTrustedDeclaration(String name, UUID uuid) {
        Capture capture = captures.getIfPresent(name);
        return capture != null && capture.claim != null && capture.uuid.equals(uuid);
    }

    // Return true for Bedrock declarations, including rejected ones, to exclude Java fallback.
    public boolean configure(Object profile, String name, UUID uuid, SocketAddress address) {
        if (!plugin.getBungeeManager().isEnabled()) {
            return false;
        }
        Capture captured = captures.asMap().remove(name);
        List<String> values;
        try {
            values = SpigotProfileReader.readProfile(profile, "configure").bedrock;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            if (captured != null) {
                decisions.put(name, new Decision(uuid, address));
            }
            return captured != null;
        }
        if (captured == null && values.isEmpty()) {
            return false;
        }
        Decision decision = new Decision(uuid, address);
        decisions.put(name, decision);
        if (captured == null || captured.claim == null || !captured.uuid.equals(uuid) || values.size() != 1
                || !captured.wire.equals(values.get(0)) || verify(captured.wire, name, uuid) == null) {
            debug("Rejected unbound, unsigned or stale proxy Bedrock facts for {}", name);
            return true;
        }
        if (!consumeNonce(captured.claim.getNonce())) {
            debug("Rejected replayed proxy Bedrock facts for {}", name);
            return true;
        }
        execute(name, captured.claim, decision);
        return true;
    }

    private void execute(String name, BedrockClaim claim, Decision decision) {
        AuthPlugin<Player> hook = plugin.getCore().getAuthPluginHook();
        if (!(hook instanceof PreLoginAuthPlugin)) {
            decision.outcome = FloodgatePreLoginAuth.Outcome.UNSUPPORTED;
        } else if (plugin.isServerFullyStarted()) {
            decision.outcome = new FloodgatePreLoginAuth(plugin.getCore()).handle(name,
                    new BedrockSession(claim.isLinked(), claim.getAutoLogin(), claim.getAutoRegister(),
                            claim.getAllowConflict()), (PreLoginAuthPlugin) hook);
            debug("Proxy Bedrock pre-login for {}: {}", name, decision.outcome);
        }
    }

    /**
     * Ownership suppresses alternatives; it does not mean authentication was requested or completed.
     *
     * @param player the actual connection's player
     * @return whether a Bedrock declaration owns this connection's decision
     */
    public boolean isHandled(Player player) {
        return decision(player) != null;
    }

    public FloodgatePreLoginAuth.Outcome getOutcome(Player player) {
        Decision decision = decision(player);
        return decision == null ? null : decision.outcome;
    }

    public boolean isAuthenticationRequested(Player player) {
        FloodgatePreLoginAuth.Outcome outcome = getOutcome(player);
        return outcome == FloodgatePreLoginAuth.Outcome.LOGIN_REQUESTED
                || outcome == FloodgatePreLoginAuth.Outcome.REGISTERED_AND_LOGIN_REQUESTED;
    }

    private Decision decision(Player player) {
        if (handledPlayers.getIfPresent(player) != null) {
            return handledPlayers.getIfPresent(player);
        }
        Decision request = decisions.getIfPresent(player.getName());
        if (request != null && request.uuid.equals(player.getUniqueId())
                && request.address != null && request.address.equals(player.spigot().getRawAddress())) {
            handledPlayers.put(player, request);
            decisions.asMap().remove(player.getName(), request);
            return request;
        }
        return null;
    }

    private BedrockClaim verify(String wire, String name, UUID uuid) {
        try {
            return BedrockClaim.verify(wire, name, uuid, plugin.getBungeeManager().getAllowedProxyIds(),
                    System.currentTimeMillis());
        } catch (RuntimeException rejected) {
            return null;
        }
    }

    private boolean consumeNonce(UUID nonce) {
        synchronized (usedNonces) {
            usedNonces.cleanUp();
            // Do not evict a still-live nonce to make room: saturation must fail closed.
            return usedNonces.size() < 65536
                    && usedNonces.asMap().putIfAbsent(nonce, Boolean.TRUE) == null;
        }
    }

    private void debug(String message, Object... args) {
        if (plugin.getCore().isDebug()) {
            plugin.getLog().info(message, args);
        }
    }

    private static final class Capture {
        private final UUID uuid;
        private final String wire;
        private final BedrockClaim claim;

        private Capture(UUID uuid, String wire, BedrockClaim claim) {
            this.uuid = uuid;
            this.wire = wire;
            this.claim = claim;
        }
    }

    private static final class Decision {
        private final UUID uuid;
        private final SocketAddress address;
        private volatile FloodgatePreLoginAuth.Outcome outcome;

        private Decision(UUID uuid, SocketAddress address) {
            this.uuid = uuid;
            this.address = address;
        }
    }
}
