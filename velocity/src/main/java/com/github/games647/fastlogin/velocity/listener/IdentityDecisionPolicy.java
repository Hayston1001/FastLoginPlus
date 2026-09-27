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
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.protocol.IdentityClaim.Decision;
import java.util.UUID;

/** Converts a per-connection session into the explicit Fabric forwarding decision. */
final class IdentityDecisionPolicy {

    private IdentityDecisionPolicy() {
    }

    /**
     * Only a Mojang verified profile with the same name and UUID can claim premium status.
     * A missing session, incomplete lookup, or unsupported Bedrock connection remains unresolved.
     *
     * @param session the connection session, if any
     * @param onlineMode whether Velocity finished online authentication
     * @param bedrock whether this is a Floodgate connection
     * @param name final forwarded profile name
     * @param uuid final forwarded profile UUID
     * @return the bound identity claim
     */
    static IdentityClaim decide(LoginSession session, boolean onlineMode, boolean bedrock,
                                String name, UUID uuid) {
        Decision decision = Decision.UNDECIDED;
        UUID verified = null;
        if (session != null && !bedrock) {
            if (onlineMode && session.getUuid() != null
                    && session.getUsername().equals(name) && session.getUuid().equals(uuid)) {
                decision = Decision.PREMIUM_VERIFIED;
                verified = session.getUuid();
                session.setIdentityDecision(decision);
            } else if (!onlineMode && session.getIdentityDecision() == Decision.OFFLINE
                    && session.getUsername().equals(name)
                    && UUIDAdapter.generateOfflineId(name).equals(uuid)) {
                decision = Decision.OFFLINE;
            }
        }
        return new IdentityClaim(decision, name, uuid, verified);
    }
}
