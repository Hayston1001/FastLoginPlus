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
package com.github.games647.fastlogin.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** A versioned identity claim carried in one authenticated forwarding profile property. */
public final class IdentityClaim {

    public static final String PROPERTY_NAME = "flp_identity";
    public static final int VERSION = 1;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** The proxy decision for this particular connection. */
    public enum Decision {
        OFFLINE, PREMIUM_VERIFIED, UNDECIDED
    }

    private final Decision decision;
    private final String playerName;
    private final UUID forwardedUuid;
    private final UUID verifiedMojangUuid;

    public IdentityClaim(Decision decision, String playerName, UUID forwardedUuid, UUID verifiedMojangUuid) {
        this.decision = Objects.requireNonNull(decision, "decision");
        if (playerName == null || !PLAYER_NAME.matcher(playerName).matches()) {
            throw new IllegalArgumentException("Invalid player name");
        }
        this.playerName = playerName;
        this.forwardedUuid = Objects.requireNonNull(forwardedUuid, "forwardedUuid");
        if ((decision == Decision.PREMIUM_VERIFIED) != (verifiedMojangUuid != null)) {
            throw new IllegalArgumentException("Mojang UUID must exist only for verified premium claims");
        }
        this.verifiedMojangUuid = verifiedMojangUuid;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getForwardedUuid() {
        return forwardedUuid;
    }

    public UUID getVerifiedMojangUuid() {
        return verifiedMojangUuid;
    }

    /**
     * Encodes a claim with an exact five-field wire shape. A name cannot contain the delimiter.
     *
     * @return property value
     */
    public String encode() {
        return VERSION + ":" + decision.name() + ":" + playerName + ":" + forwardedUuid + ":"
                + (verifiedMojangUuid == null ? "-" : verifiedMojangUuid.toString());
    }

    /**
     * Parses exactly one property and binds it to the forwarding profile.
     *
     * @param values values of properties named {@link #PROPERTY_NAME}
     * @param expectedName actual forwarded profile name
     * @param expectedUuid actual forwarded profile UUID
     * @return validated claim
     */
    public static IdentityClaim parseExactlyOne(Iterable<String> values, String expectedName, UUID expectedUuid) {
        Objects.requireNonNull(values, "values");
        Iterator<String> iterator = values.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("Missing identity claim");
        }
        String value = iterator.next();
        if (iterator.hasNext()) {
            throw new IllegalArgumentException("Duplicate identity claim");
        }
        if (value == null || value.length() > 128) {
            throw new IllegalArgumentException("Invalid identity claim length");
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 5 || !Integer.toString(VERSION).equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported identity claim format");
        }
        Decision decision;
        try {
            decision = Decision.valueOf(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown identity decision", ex);
        }
        UUID forwarded = parseUuid(parts[3]);
        UUID verified = "-".equals(parts[4]) ? null : parseUuid(parts[4]);
        IdentityClaim claim = new IdentityClaim(decision, parts[2], forwarded, verified);
        if (!claim.playerName.equals(expectedName) || !claim.forwardedUuid.equals(expectedUuid)) {
            throw new IllegalArgumentException("Identity claim does not match forwarding profile");
        }
        return claim;
    }

    /**
     * Applies the v1 Fabric policy after the transport has authenticated the forwarded profile.
     * Unresolved decisions and UUID rewrites are rejected until a separate compatibility path is
     * verified. Offline UUIDs must match the exact case-sensitive player name. This makes
     * {@code premiumUuid: false} unavailable for the initial 26.2 release.
     *
     * @param values identity properties in the forwarded profile
     * @param expectedName forwarded profile name
     * @param expectedUuid forwarded profile UUID
     * @return an actionable offline or premium decision
     */
    public static IdentityClaim parseForFabric(Iterable<String> values, String expectedName, UUID expectedUuid) {
        IdentityClaim claim = parseExactlyOne(values, expectedName, expectedUuid);
        if (claim.decision == Decision.UNDECIDED) {
            throw new IllegalArgumentException("Identity decision is unresolved");
        }
        if (claim.decision == Decision.PREMIUM_VERIFIED
                && !claim.forwardedUuid.equals(claim.verifiedMojangUuid)) {
            throw new IllegalArgumentException("Premium UUID rewrite is unsupported");
        }
        if (claim.decision == Decision.OFFLINE
                && !claim.forwardedUuid.equals(UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + claim.playerName).getBytes(StandardCharsets.UTF_8)))) {
            throw new IllegalArgumentException("Offline UUID does not match player name");
        }
        return claim;
    }

    private static UUID parseUuid(String value) {
        if (!UUID_TEXT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid UUID in identity claim");
        }
        return UUID.fromString(value);
    }
}
