/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.model.skin.Textures;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public class PaperCacheListener implements Listener {

    private final FastLoginBukkit plugin;

    public PaperCacheListener(final FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    // On Paper, profile.complete(true) is called right after AsyncPlayerPreLoginEvent.
    // If the profile lacks textures at that point, Paper fills them from its filledProfileCache
    // (which may contain a stale skin from a previous session).
    // Setting the skin here ensures the profile already has correct textures before complete(true).
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        // remember this login's attestation before Paper's filledProfileCache can hand
        // the configuration phase a stale premium profile from an earlier session.
        plugin.recordPreLoginAttestation(event.getName(), event.getPlayerProfile());

        if (event.getLoginResult() != Result.ALLOWED) {
            return;
        }

        // Paper has no PlayerLoginEvent handler: reject before configuration or join instead.
        if (!plugin.isServerFullyStarted()) {
            event.disallow(Result.KICK_OTHER, plugin.getCore().getMessage("not-started"));
            return;
        }

        // Before configuration/preJoin: AuthMe must not trust an unsigned handshake's v4 UUID.
        if (plugin.isUntrustedPremiumConnection(event.getPlayerProfile(), event.getName(), event.getUniqueId())) {
            event.disallow(Result.KICK_OTHER, plugin.getCore().getMessage("proxy-claim-required"));
            return;
        }

        if (!plugin.getConfig().getBoolean("forwardSkin")) {
            return;
        }

        // The event exposes only the client IP, not the port the session cache is keyed by, but
        // it does expose the UUID this connection carries. Match on that identifier: a name alone
        // would let concurrent logins with the same name read each other's skin. No match means
        // no skin — never fall back to another connection's session.
        BukkitLoginSession session = findSession(plugin.getLoginSessions().values(),
                event.getName(), event.getUniqueId());
        if (session == null) {
            return;
        }

        // SkinsRestorer wins when it has a custom skin for the player. Look up both UUIDs this
        // login can have: the verified Mojang one, and the one the connection actually carries —
        // they differ under premiumUuid:false and SkinsRestorer is keyed by the latter. Write its
        // skin instead of the empty placeholder used until 0.7.0: that placeholder counted as
        // "online properties" for SkinsRestorer while destroying the skin its own pre-login
        // handler (default priority, i.e. before this one) had just applied, so players
        // connected with the default skin instead of their custom one.
        ProfileProperty customSkin = skinPropertyFor(plugin.getSkinsRestorerCompat()
                .getCustomSkin(session.getUuid(), event.getUniqueId()));
        if (customSkin != null) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Skipping FastLogin skin for {} — SkinsRestorer custom skin detected",
                session.getUsername());
            }
            event.getPlayerProfile().setProperty(customSkin);
            return;
        }

        session.getSkin().ifPresent(skin -> event.getPlayerProfile().setProperty(new ProfileProperty(Textures.KEY,
                skin.getValue(), skin.getSignature())));
    }

    /**
     * Finds the login session belonging to this connection.
     * <p>
     * The session cache is keyed by address including the port, which the pre-login event does
     * not carry, so the connection UUID recorded in the session is the only identifier that ties
     * a cached session to the connection being admitted. A session without a recorded connection
     * UUID never matches.
     * <p>
     * Two cached sessions can share the same name and connection UUID, for example the player's
     * previous session when the entry has not expired yet. They may only stand in for one another
     * when both are the same verified identity; otherwise this connection cannot be told apart and
     * receives no skin rather than a possibly foreign one.
     *
     * @param sessions       the cached login sessions
     * @param name           the connecting player's name
     * @param connectionUuid the UUID the connection carries
     * @return the matching session, or null when there is none or the match is ambiguous
     */
    private static BukkitLoginSession findSession(Collection<BukkitLoginSession> sessions, String name,
                                                  UUID connectionUuid) {
        if (connectionUuid == null) {
            return null;
        }

        BukkitLoginSession match = null;
        for (BukkitLoginSession session : sessions) {
            if (!name.equals(session.getUsername())) {
                continue;
            }
            if (!connectionUuid.equals(session.getConnectionUuid())) {
                continue;
            }

            if (match == null) {
                match = session;
                continue;
            }

            if (!sameVerifiedIdentity(match, session)) {
                return null;
            }

            // Same identity (e.g. an expired-but-still-cached previous session): prefer the
            // entry that actually carries a skin.
            if (!match.getSkin().isPresent() && session.getSkin().isPresent()) {
                match = session;
            }
        }
        return match;
    }

    private static boolean sameVerifiedIdentity(BukkitLoginSession first, BukkitLoginSession second) {
        return first.isVerifiedPremium() && second.isVerifiedPremium()
                && Objects.equals(first.getUuid(), second.getUuid());
    }

    /**
     * Builds the profile property for a skin SkinsRestorer owns.
     * <p>
     * Returns {@code null} when there is nothing usable to write — either SkinsRestorer answered
     * with no skin, or the stored value is empty. An empty {@code textures} value must never reach
     * the profile: it counts as "online properties present" for SkinsRestorer and, because this
     * listener runs at {@link EventPriority#HIGHEST}, it would overwrite whatever that plugin
     * applied from its own pre-login handler.
     *
     * @param customSkin the skin SkinsRestorer stored for this player, may be null
     * @return the property to set, or null to leave the profile untouched
     */
    static ProfileProperty skinPropertyFor(SkinProperty customSkin) {
        if (customSkin == null || customSkin.getValue() == null || customSkin.getValue().isEmpty()) {
            return null;
        }

        return new ProfileProperty(Textures.KEY, customSkin.getValue(), customSkin.getSignature());
    }

}
