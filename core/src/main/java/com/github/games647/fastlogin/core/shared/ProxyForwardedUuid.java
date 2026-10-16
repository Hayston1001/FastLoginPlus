/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.UUIDAdapter;

import java.util.UUID;

/**
 * Recognises a possible forwarded Mojang UUID by shape, without authenticating its source.
 * Offline UUIDs are name-derived version 3; Mojang UUIDs are version 4. A client that can
 * reach an unsigned legacy backend can also supply a version-4 UUID. Callers must separately
 * require signed forwarding or a validated identity declaration for that same connection.
 * Paper/Folia read the profile during configure; Spigot can read its live PlayerLoginEvent profile.
 *
 * <p><strong>Scope:</strong> this is only meaningful while the proxy runs with
 * {@code premiumUuid: true}. With {@code premiumUuid: false} the proxy deliberately
 * rewrites the UUID to the offline one, the signal disappears, and the caller has to
 * fall back to the post-join path (see {@code ForceLoginManagement}'s register branch,
 * which logs the pre-created record in so AuthMe closes its own dialog).</p>
 */
public final class ProxyForwardedUuid {

    private ProxyForwardedUuid() {
        // utility class
    }

    /**
     * Returns whether {@code connectionUuid} has the shape of a forwarded Mojang UUID.
     *
     * @param connectionUuid the UUID the connection carries (may be {@code null})
     * @param playerName     the requested player name (may be {@code null})
     * @return true for a structural candidate, never proof of proxy authentication
     */
    public static boolean isForwardedMojangUuid(UUID connectionUuid, String playerName) {
        if (connectionUuid == null || playerName == null) {
            return false;
        }

        // Offline UUIDs are always version 3 (name-derived). A version-4 UUID cannot be
        // one, but this shape alone does not distinguish a proxy from a direct forgery.
        // Kept as a second, explicit check so a future change of the offline-UUID
        // scheme cannot silently turn this predicate into "any non-null UUID".
        if (connectionUuid.version() != 4) {
            return false;
        }

        return !connectionUuid.equals(UUIDAdapter.generateOfflineId(playerName));
    }
}
