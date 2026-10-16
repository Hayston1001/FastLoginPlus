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
package com.github.games647.fastlogin.core.message;

import com.github.games647.fastlogin.protocol.IdentityClaim;
import com.github.games647.fastlogin.protocol.ConnectionDeclaration;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import java.util.UUID;

public class LoginActionMessage implements ChannelMessage {

    public static final String FORCE_CHANNEL = "force";

    private Type type;

    private String playerName;
    private UUID proxyId;

    /**
     * Mojang (version 4) UUID the proxy already verified for this player.
     * <p>
     * {@code null} when the proxy has nothing to declare: the connection was not verified as
     * premium (cracked login, Floodgate, forced login), or the proxy predates this field.
     * <p>
     * The backend used to have no way of learning the verified UUID when
     * {@code premiumUuid: false} — there the proxy deliberately rewrites the forwarded UUID to
     * the offline one, so the regular connection UUID carries no premium signal and AuthMe would
     * never get a {@code premium_uuid} written. The proxy has always known this value (see
     * {@code ConnectListener.onGameProfileRequest} / {@code onLogin}, which store the immutable
     * Mojang UUID on the session); it was simply never transmitted.
     */
    private UUID verifiedPremiumUuid;

    /** Everything the proxy declared about this connection (facts + policy). */
    private IdentityClaim.Facts facts = IdentityClaim.Facts.defaults();
    private ConnectionDeclaration declaration;

    public void setDeclaration(IdentityClaim.Decision decision, UUID forwardedUuid) {
        this.declaration = new ConnectionDeclaration(decision, forwardedUuid);
        validateDeclaration();
    }

    public IdentityClaim.Decision getIdentityDecision() {
        if (type == Type.CRACKED) {
            return IdentityClaim.Decision.OFFLINE;
        }
        return declaration == null ? (verifiedPremiumUuid == null ? IdentityClaim.Decision.UNDECIDED
                : IdentityClaim.Decision.PREMIUM_VERIFIED) : declaration.getDecision();
    }

    public UUID getForwardedUuid() {
        return declaration == null ? null : declaration.getForwardedUuid();
    }

    private void validateDeclaration() {
        if (type == Type.CRACKED && verifiedPremiumUuid != null) {
            throw new IllegalArgumentException("Unverified declaration cannot carry a premium UUID");
        }
        if (declaration != null) {
            IdentityClaim.Decision decision = declaration.getDecision();
            if ((type == Type.CRACKED) != (decision == IdentityClaim.Decision.OFFLINE)
                    || (decision == IdentityClaim.Decision.PREMIUM_VERIFIED && verifiedPremiumUuid == null)
                    || (decision != IdentityClaim.Decision.PREMIUM_VERIFIED && verifiedPremiumUuid != null)) {
                throw new IllegalArgumentException("Contradictory login identity declaration");
            }
        }
    }

    public LoginActionMessage(Type type, String playerName, UUID proxyId, UUID verifiedPremiumUuid) {
        this.type = type;
        this.playerName = playerName;
        this.proxyId = proxyId;
        this.verifiedPremiumUuid = verifiedPremiumUuid;
    }

    /**
     * Sets the identity facts and policy keys this message declares.
     *
     * @param facts the proxy's fact bundle for this connection
     */
    public void setFacts(IdentityClaim.Facts facts) {
        this.facts = java.util.Objects.requireNonNull(facts, "facts");
    }

    public LoginActionMessage() {
        //reading mode
    }

    public Type getType() {
        return type;
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getProxyId() {
        return proxyId;
    }

    /**
     * Gets the Mojang UUID the proxy verified for this player.
     *
     * @return the verified premium UUID, or null if the proxy declared none
     */
    public UUID getVerifiedPremiumUuid() {
        return verifiedPremiumUuid;
    }

    /**
     * The declared facts and policy of this connection.
     *
     * @return the proxy's fact bundle for this connection
     */
    public IdentityClaim.Facts getFacts() {
        return facts;
    }

    /**
     * Whether the proxy has no record for this name.
     *
     * @return true when the name is new to FastLoginPlus
     */
    public boolean isNewPlayer() {
        return facts.newPlayer();
    }

    /**
     * Whether a premium name was observed without a passing verification.
     *
     * @return true for the same-name cracked observation
     */
    public boolean isSameNameCracked() {
        return facts.sameNameCracked();
    }

    /**
     * The name's upgrade consent as recorded on the proxy.
     *
     * @return consent state, unknown when the proxy declared none
     */
    public IdentityClaim.Consent getConsent() {
        return facts.consent();
    }

    /**
     * Whether auto-login for verified premium identities is enabled (proxy policy).
     *
     * @return the proxy's autoLogin value
     */
    public boolean isAutoLogin() {
        return facts.autoLogin();
    }

    /**
     * Whether verified identities without a local account are auto-registered
     * (proxy policy).
     *
     * @return the proxy's auto-register-unknown value
     */
    public boolean isAutoRegisterUnknown() {
        return facts.autoRegisterUnknown();
    }

    /**
     * Whether unregistered new players are rejected (proxy policy).
     *
     * @return the proxy's offline-whitelist value
     */
    public boolean isOfflineWhitelist() {
        return facts.offlineWhitelist();
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
        // validate the type byte — a malformed client plugin message
        // would otherwise throw an uncaught ArrayIndexOutOfBoundsException
        byte typeByte = input.readByte();
        if (typeByte < 0 || typeByte >= Type.values().length) {
            throw new IllegalArgumentException("Invalid " + getChannelName() + " type byte: " + typeByte);
        }
        this.type = Type.values()[typeByte];

        this.playerName = input.readUTF();

        //bungeecord UUID
        long mostSignificantBits = input.readLong();
        long leastSignificantBits = input.readLong();
        this.proxyId = new UUID(mostSignificantBits, leastSignificantBits);

        // optional trailing field. A proxy older than this field sends nothing here,
        // and Guava's ByteArrayDataInput surfaces the resulting EOF as an IllegalStateException
        // (its read methods wrap the checked EOFException). Absence is a supported state, not an
        // error — it means "this proxy has nothing to declare". The caller decides what to do
        // with a null; a writer with nothing to declare sends the zero UUID, which the version-4
        // check in resolvePremiumUuid rejects.
        try {
            long uuidMostSignificantBits = input.readLong();
            long uuidLeastSignificantBits = input.readLong();
            // The zero UUID is this field's wire encoding of "nothing declared". Normalise it back
            // to null so the round trip is symmetric and consumers only ever see a real UUID or
            // null — never a placeholder that looks like a value. No real Mojang UUID can
            // collide with it: those are version 4, and the zero UUID is version 0.
            this.verifiedPremiumUuid =
                    (uuidMostSignificantBits == 0L && uuidLeastSignificantBits == 0L)
                            ? null
                            : new UUID(uuidMostSignificantBits, uuidLeastSignificantBits);
        } catch (IllegalStateException truncated) {
            this.verifiedPremiumUuid = null;
        }

        // optional trailing identity facts and policy keys. A proxy older than
        // them sends nothing here; absence means "no observation, no consent
        // declaration", which consumers treat as the conservative state.
        try {
            boolean newPlayer = input.readBoolean();
            boolean sameNameCracked = input.readBoolean();
            IdentityClaim.Consent consent = consentOf(input.readByte());
            boolean autoLogin = input.readBoolean();
            boolean autoRegisterUnknown = input.readBoolean();
            boolean offlineWhitelist = input.readBoolean();
            this.facts = new IdentityClaim.Facts(newPlayer, sameNameCracked, consent,
                    autoLogin, autoRegisterUnknown, offlineWhitelist);
        } catch (IllegalStateException truncated) {
            this.facts = IdentityClaim.Facts.defaults();
        }
        // Backends must be upgraded first. Older readers ignore this complete
        // suffix; CRACKED remains their status-only operation, never a grant.
        int marker;
        try {
            marker = input.readUnsignedByte();
        } catch (IllegalStateException absent) {
            declaration = null;
            validateDeclaration();
            return;
        }
        if (marker != ConnectionDeclaration.MARKER) {
            throw new IllegalArgumentException("Invalid connection declaration marker");
        }
        try {
            declaration = ConnectionDeclaration.readPayload(input);
        } catch (java.io.IOException | IllegalStateException truncated) {
            throw new IllegalArgumentException("Truncated connection declaration", truncated);
        }
        validateDeclaration();
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
        validateDeclaration();
        output.writeByte(type.ordinal());

        //Data is sent through a random player. We have to tell the Bukkit version of this plugin the target
        output.writeUTF(playerName);

        //proxy identifier to check if it's an acceptable proxy
        output.writeLong(proxyId.getMostSignificantBits());
        output.writeLong(proxyId.getLeastSignificantBits());

        // proxy-verified Mojang UUID. Written unconditionally so the frame length is
        // deterministic; the zero UUID means "nothing to declare" and is filtered out by
        // resolvePremiumUuid's version-4 check. Old backends stop reading after proxyId and
        // ignore the trailing bytes, so they are unaffected.
        if (verifiedPremiumUuid == null) {
            output.writeLong(0L);
            output.writeLong(0L);
        } else {
            output.writeLong(verifiedPremiumUuid.getMostSignificantBits());
            output.writeLong(verifiedPremiumUuid.getLeastSignificantBits());
        }

        // identity facts (observation + consent) and policy keys (proxy-owned,
        // backend-enforced). Trailing, so old backends stop reading earlier and
        // unaffected ones keep their conservative defaults.
        output.writeBoolean(facts.newPlayer());
        output.writeBoolean(facts.sameNameCracked());
        output.writeByte(consentCode(facts.consent()));
        output.writeBoolean(facts.autoLogin());
        output.writeBoolean(facts.autoRegisterUnknown());
        output.writeBoolean(facts.offlineWhitelist());
        if (declaration != null) {
            try {
                declaration.write(output);
            } catch (java.io.IOException impossible) {
                throw new IllegalStateException("Cannot encode connection declaration", impossible);
            }
        }
    }

    private static byte consentCode(IdentityClaim.Consent consent) {
        if (consent == IdentityClaim.Consent.YES) {
            return 1;
        }
        if (consent == IdentityClaim.Consent.NO) {
            return 2;
        }
        return 0;
    }

    private static IdentityClaim.Consent consentOf(byte code) {
        if (code == 1) {
            return IdentityClaim.Consent.YES;
        }
        if (code == 2) {
            return IdentityClaim.Consent.NO;
        }
        return IdentityClaim.Consent.UNKNOWN;
    }

    @Override
    public String getChannelName() {
        return FORCE_CHANNEL;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "type='" + type + '\''
            + ", playerName='" + playerName + '\''
            + ", proxyId=" + proxyId
            + ", verifiedPremiumUuid=" + verifiedPremiumUuid
            + '}';
    }

    public enum Type {

        LOGIN,

        REGISTER,

        /** Unverified connection declaration; never a request to authenticate. */
        CRACKED
    }
}
