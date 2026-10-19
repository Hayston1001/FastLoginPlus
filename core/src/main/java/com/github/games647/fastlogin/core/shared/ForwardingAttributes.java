/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.protocol.IdentityClaim;

/**
 * Name of the custom GameProfile property the proxy uses to hand the verified Mojang UUID to
 * the backend.
 *
 * <p>With {@code premiumUuid: false} the proxy rewrites the forwarded UUID to the offline one, so
 * the backend cannot tell a verified premium connection from a cracked one and AuthMe shows its
 * preJoin dialog on the very first login. A separate plugin message cannot reach the backend that
 * early, so the proxy attaches the value as a property on the GameProfile it is already rewriting,
 * riding Velocity's modern player-information forwarding; the backend reads it back in the
 * configuration phase, before any dialog exists.</p>
 *
 * <p>Trust boundary: the forwarding handshake's HMAC protects the payload in transit, but the
 * authority it certifies is <em>the proxy process as a whole</em>, not this plugin — any code on
 * the proxy can inject the same property. The backend therefore reads this as "the proxy attests",
 * never "FLP attests". The capability is not new either: the configure-phase auto-register already
 * trusted the payload's connection UUID, which a holder of the forwarding secret could set to the
 * victim's Mojang UUID. That is also why the property carries no second signature scheme — one
 * would not close the pre-existing path anyway.</p>
 *
 * <p><b>Two transports, two names.</b> BungeeCord's legacy forwarding appends the login profile's
 * properties to the handshake's host field as JSON, so the same attestation can ride it — but Paper
 * rebuilds the profile from that payload through a name filter, which is why that path cannot reuse
 * {@link #PREMIUM_UUID}. Both names are read by the backend, so a network mixing proxy software (or
 * running an older jar on one side) keeps working.</p>
 */
public final class ForwardingAttributes {

    /** Fabric backend identity claim, authenticated by the forwarding transport. */
    public static final String IDENTITY = IdentityClaim.PROPERTY_NAME;

    /**
     * Carries the Mojang UUID the proxy verified for this connection, over Velocity's modern
     * player-information forwarding. Written only when the connection actually was verified as
     * premium; absent otherwise (cracked, Floodgate).
     */
    public static final String PREMIUM_UUID = "flp-premium-uuid";

    /**
     * The same attestation over BungeeCord's legacy forwarding, where the name cannot be
     * {@link #PREMIUM_UUID}.
     *
     * <p>The reason is mechanical: Paper rebuilds the player profile from the legacy handshake
     * and discards every property whose name does not match {@code \w{0,16}} — letters, digits
     * and underscores only, at most 16 characters — so a name containing hyphens disappears
     * there without an error or a log line. Velocity's modern forwarding reads the names
     * verbatim, which is why that transport keeps the original spelling.</p>
     */
    public static final String PREMIUM_UUID_LEGACY = "flp_premium_uuid";

    /**
     * Whether a GameProfile property name carries FLP's premium attestation, over either
     * transport.
     *
     * <p>The backend does not know — and should not need to care — which proxy software
     * forwarded the connection, so both spellings are accepted here rather than in the reader.
     * </p>
     *
     * @param name the property name to test
     * @return true when the property is FLP's premium attestation
     */
    public static boolean isPremiumUuidProperty(String name) {
        return PREMIUM_UUID.equals(name) || PREMIUM_UUID_LEGACY.equals(name);
    }

    private ForwardingAttributes() {
    }
}
