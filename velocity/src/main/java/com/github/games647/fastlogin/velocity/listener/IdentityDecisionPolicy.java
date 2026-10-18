/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
     * Only a Mojang verified profile with the same name can claim premium status. Its UUID must
     * match the verified UUID, or the name-derived offline UUID when premium UUIDs are disabled.
     * A missing session, incomplete lookup, or unsupported Bedrock connection remains unresolved.
     *
     * @param session the connection session, if any
     * @param onlineMode whether Velocity finished online authentication
     * @param bedrock whether this is a Floodgate connection
     * @param premiumUuid whether premium players must keep their verified UUID
     * @param name final forwarded profile name
     * @param uuid final forwarded profile UUID
     * @return the bound identity claim
     */
    static IdentityClaim decide(LoginSession session, boolean onlineMode, boolean bedrock, boolean premiumUuid,
                                String name, UUID uuid) {
        Decision decision = Decision.UNDECIDED;
        UUID verified = null;
        if (session != null && !bedrock) {
            if (onlineMode && session.getUuid() != null
                    && session.getUsername().equals(name)
                    && (session.getUuid().equals(uuid)
                    || (!premiumUuid && UUIDAdapter.generateOfflineId(name).equals(uuid)))) {
                decision = Decision.PREMIUM_VERIFIED;
                verified = session.getUuid();
                session.setIdentityDecision(decision);
            } else if (!onlineMode && session.getIdentityDecision() == Decision.OFFLINE
                    && session.getUsername().equals(name)
                    && UUIDAdapter.generateOfflineId(name).equals(uuid)) {
                decision = Decision.OFFLINE;
            }
        }
        boolean newPlayer = session != null && session.isNewPlayer();
        boolean sameNameCracked = session != null && session.isSameNameCracked();
        IdentityClaim.Consent consent =
                session == null ? IdentityClaim.Consent.UNKNOWN : session.getConsent();
        boolean autoLogin = session == null || session.isAutoLogin();
        boolean autoRegisterUnknown = session != null && session.isAutoRegisterUnknown();
        boolean offlineWhitelist = session != null && session.isOfflineWhitelist();
        return new IdentityClaim(decision, name, uuid, verified,
                new IdentityClaim.Facts(newPlayer, sameNameCracked, consent,
                        autoLogin, autoRegisterUnknown, offlineWhitelist));
    }
}
