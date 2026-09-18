/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.UUIDAdapter;

import java.util.UUID;

/**
 * Recognises the UUID a proxy forwarded for a premium player, without reading the
 * forwarded profile property (0.7.0/F24).
 *
 * <p>On Paper/Folia the proxy's attestation travels as a GameProfile property that the
 * backend can read in the configuration phase. Spigot has no configuration phase and no
 * access to that property before the join (its {@code AsyncPlayerPreLoginEvent} has no
 * profile accessor, and the forwarding payload is stripped from the hostname), so the
 * only signal left is the UUID itself — and it is sufficient:</p>
 *
 * <ul>
 *   <li>FastLogin derives an offline UUID with {@code UUIDAdapter.generateOfflineId},
 *       which is MD5-based and therefore <strong>always version 3</strong>;</li>
 *   <li>a <strong>version 4</strong> UUID therefore cannot have been derived from the
 *       name — on a backend behind a proxy it can only have come from the proxy's
 *       handshake, and with {@code premiumUuid: true} the proxy keeps the Mojang UUID
 *       only for connections it verified as premium.</li>
 * </ul>
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
     * Returns whether {@code connectionUuid} is the Mojang UUID a proxy forwarded for
     * {@code playerName}.
     *
     * @param connectionUuid the UUID the connection carries (may be {@code null})
     * @param playerName     the requested player name (may be {@code null})
     * @return true when the UUID can only have been forwarded by the proxy
     */
    public static boolean isForwardedMojangUuid(UUID connectionUuid, String playerName) {
        if (connectionUuid == null || playerName == null) {
            return false;
        }

        // Offline UUIDs are always version 3 (name-derived). A version-4 UUID cannot be
        // one, so it must have been sent by the proxy rather than derived locally.
        // Kept as a second, explicit check so a future change of the offline-UUID
        // scheme cannot silently turn this predicate into "any non-null UUID".
        if (connectionUuid.version() != 4) {
            return false;
        }

        return !connectionUuid.equals(UUIDAdapter.generateOfflineId(playerName));
    }
}
