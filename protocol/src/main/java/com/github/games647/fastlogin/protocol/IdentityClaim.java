/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** A versioned identity claim carried in one authenticated forwarding profile property. */
public final class IdentityClaim {

    public static final String PROPERTY_NAME = "flp_identity";
    public static final String SIGNATURE_PROPERTY_NAME = "flp_identity_sig";
    public static final int VERSION = 1;
    private static final String SIGNATURE_DOMAIN = "fastloginplus:flp_identity:v1\0";
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** The proxy decision for this particular connection. */
    public enum Decision {
        OFFLINE, PREMIUM_VERIFIED, UNDECIDED
    }

    /** Upgrade consent of the name's FastLoginPlus record. */
    public enum Consent {
        /** The record's owner explicitly upgraded this name to premium. */
        YES,
        /** The record exists without an upgrade consent. */
        NO,
        /** Nothing was declared (older proxy, or no session). */
        UNKNOWN
    }

    /** Everything the proxy declares about one connection beyond the decision. */
    public static final class Facts {

        private final boolean newPlayer;
        private final boolean sameNameCracked;
        private final Consent consent;
        private final boolean autoLogin;
        private final boolean autoRegisterUnknown;
        private final boolean offlineWhitelist;
        private final boolean declared;

        public Facts(boolean newPlayer, boolean sameNameCracked, Consent consent,
                     boolean autoLogin, boolean autoRegisterUnknown, boolean offlineWhitelist) {
            this(newPlayer, sameNameCracked, consent, autoLogin, autoRegisterUnknown, offlineWhitelist, true);
        }

        public Facts(boolean newPlayer, boolean sameNameCracked, Consent consent,
                     boolean autoLogin, boolean autoRegisterUnknown, boolean offlineWhitelist,
                     boolean policyDeclared) {
            this.newPlayer = newPlayer;
            this.sameNameCracked = sameNameCracked;
            this.consent = Objects.requireNonNull(consent, "consent");
            this.autoLogin = autoLogin;
            this.autoRegisterUnknown = autoRegisterUnknown;
            this.offlineWhitelist = offlineWhitelist;
            this.declared = policyDeclared;
        }

        /**
         * The shape older proxies (or missing sessions) leave behind.
         *
         * @return the conservative fact bundle
         */
        public static Facts defaults() {
            return new Facts(false, false, Consent.UNKNOWN, true, false, false, false);
        }

        public boolean newPlayer() {
            return newPlayer;
        }

        public boolean sameNameCracked() {
            return sameNameCracked;
        }

        public Consent consent() {
            return consent;
        }

        public boolean autoLogin() {
            return autoLogin;
        }

        public boolean autoRegisterUnknown() {
            return autoRegisterUnknown;
        }

        public boolean offlineWhitelist() {
            return offlineWhitelist;
        }

        /**
         * Whether a proxy actually declared the policy keys, as opposed to an
         * older proxy (or a facts-only declaration) leaving the conservative
         * defaults behind. The observation facts and the consent use their own
         * values: an undeclared consent simply stays {@link Consent#UNKNOWN}.
         *
         * @return true when the policy keys came from a declaring proxy
         */
        public boolean policyDeclared() {
            return declared;
        }
    }

    private final Decision decision;
    private final String playerName;
    private final UUID forwardedUuid;
    private final UUID verifiedMojangUuid;
    private final Facts facts;
    private final UUID proxyId;

    public IdentityClaim(Decision decision, String playerName, UUID forwardedUuid, UUID verifiedMojangUuid) {
        this(decision, playerName, forwardedUuid, verifiedMojangUuid, Facts.defaults(), null);
    }

    public IdentityClaim(Decision decision, String playerName, UUID forwardedUuid, UUID verifiedMojangUuid,
                         Facts facts) {
        this(decision, playerName, forwardedUuid, verifiedMojangUuid, facts, null);
    }

    public IdentityClaim(Decision decision, String playerName, UUID forwardedUuid, UUID verifiedMojangUuid,
                         Facts facts, UUID proxyId) {
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
        this.facts = Objects.requireNonNull(facts, "facts");
        this.proxyId = proxyId;
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
     * The declared facts and policy of this connection.
     *
     * @return the fact bundle the proxy sent
     */
    public Facts getFacts() {
        return facts;
    }

    /**
     * Whether the proxy has no record for this name (observation fact).
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
    public Consent getConsent() {
        return facts.consent();
    }

    /**
     * Whether auto-login for verified premium identities is enabled (policy key,
     * lives on the proxy and is only enforced here).
     *
     * @return the proxy's autoLogin value
     */
    public boolean isAutoLogin() {
        return facts.autoLogin();
    }

    /**
     * Whether verified identities without a local account are auto-registered
     * (policy key, lives on the proxy).
     *
     * @return the proxy's auto-register-unknown value
     */
    public boolean isAutoRegisterUnknown() {
        return facts.autoRegisterUnknown();
    }

    /**
     * Whether unregistered new players are rejected (policy key, lives on the
     * proxy; the known list is the proxy's record set).
     *
     * @return the proxy's offline-whitelist value
     */
    public boolean isOfflineWhitelist() {
        return facts.offlineWhitelist();
    }

    /**
     * Historical plaintext proxy identifier, retained only for wire compatibility.
     * It does not authenticate legacy forwarding. New Bungee senders omit it and
     * sign the claim with the existing proxy UUID instead.
     *
     * @return the proxy UUID, or null when the sender declared none
     */
    public UUID getProxyId() {
        return proxyId;
    }

    /**
     * Encodes a claim with an exact wire shape. A name cannot contain the delimiter.
     *
     * @return property value
     */
    public String encode() {
        return VERSION + ":" + decision.name() + ":" + playerName + ":" + forwardedUuid + ":"
                + (verifiedMojangUuid == null ? "-" : verifiedMojangUuid.toString()) + ":"
                + (facts.newPlayer() ? "Y" : "N") + ":" + (facts.sameNameCracked() ? "Y" : "N") + ":"
                + (facts.consent() == Consent.UNKNOWN ? "-"
                : facts.consent() == Consent.YES ? "Y" : "N") + ":"
                + (facts.autoLogin() ? "Y" : "N") + ":" + (facts.autoRegisterUnknown() ? "Y" : "N") + ":"
                + (facts.offlineWhitelist() ? "Y" : "N") + ":"
                + (proxyId == null ? "-" : proxyId.toString());
    }

    /**
     * Signs the exact legacy-forwarded value using the existing proxy UUID as the key.
     * This authenticates the claim, not its freshness; captured claims can still be replayed.
     * The UUID must remain private even though it is not a dedicated forwarding secret.
     *
     * @param value exact encoded claim, without normalization
     * @param proxyId existing allowed proxy UUID
     * @return URL-safe Base64 HMAC-SHA256 without padding
     */
    public static String sign(String value, UUID proxyId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature(value, proxyId));
    }

    /**
     * Verifies a legacy claim signature with a constant-time MAC comparison.
     *
     * @param value exact received claim value
     * @param signature URL-safe Base64 signature without padding
     * @param proxyId candidate allowed proxy UUID
     * @return false for absent, malformed or nonmatching signatures
     */
    public static boolean verifySignature(String value, String signature, UUID proxyId) {
        if (value == null || proxyId == null || signature == null
                || !signature.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        try {
            byte[] received = Base64.getUrlDecoder().decode(signature);
            return MessageDigest.isEqual(signature(value, proxyId), received);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static byte[] signature(String value, UUID proxyId) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(proxyId, "proxyId");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(proxyId.toString().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(SIGNATURE_DOMAIN.getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Identity signature unavailable", unavailable);
        }
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
        if (value == null || value.length() > 192) {
            throw new IllegalArgumentException("Invalid identity claim length");
        }
        String[] parts = value.split(":", -1);
        // five mandatory fields plus optional trailing fact, policy and proxy
        // fields; a proxy that predates them sends the short shape and the facts
        // stay unknown
        if ((parts.length != 5 && parts.length != 8 && parts.length != 11 && parts.length != 12)
                || !Integer.toString(VERSION).equals(parts[0])) {
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
        boolean newPlayer = false;
        boolean sameNameCracked = false;
        Consent consent = Consent.UNKNOWN;
        boolean autoLogin = true;
        boolean autoRegisterUnknown = false;
        boolean offlineWhitelist = false;
        boolean declared = false;
        if (parts.length >= 8) {
            newPlayer = parseFlag(parts[5], "new player");
            sameNameCracked = parseFlag(parts[6], "same-name cracked");
            consent = parseConsent(parts[7]);
        }
        if (parts.length >= 11) {
            autoLogin = parseFlag(parts[8], "auto login");
            autoRegisterUnknown = parseFlag(parts[9], "auto register unknown");
            offlineWhitelist = parseFlag(parts[10], "offline whitelist");
            declared = true;
        }
        UUID proxyId = null;
        if (parts.length == 12 && !"-".equals(parts[11])) {
            proxyId = parseUuid(parts[11]);
        }
        IdentityClaim claim = new IdentityClaim(decision, parts[2], forwarded, verified,
                new Facts(newPlayer, sameNameCracked, consent, autoLogin, autoRegisterUnknown,
                        offlineWhitelist, declared),
                proxyId);
        if (!claim.playerName.equals(expectedName) || !claim.forwardedUuid.equals(expectedUuid)) {
            throw new IllegalArgumentException("Identity claim does not match forwarding profile");
        }
        return claim;
    }

    /**
     * Applies the v1 Fabric policy after the transport has authenticated the forwarded profile.
     * Premium claims must forward the verified Mojang UUID or the offline UUID derived from the
     * exact case-sensitive player name. The latter is the {@code premiumUuid: false} downgrade,
     * not an arbitrary UUID rewrite. Unresolved decisions remain rejected, and offline claims
     * must forward the name-derived offline UUID.
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
                && !claim.forwardedUuid.equals(claim.verifiedMojangUuid)
                && !claim.forwardedUuid.equals(UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + claim.playerName).getBytes(StandardCharsets.UTF_8)))) {
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

    private static boolean parseFlag(String value, String fact) {
        if ("Y".equals(value)) {
            return true;
        }
        if ("N".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid " + fact + " fact in identity claim");
    }

    private static Consent parseConsent(String value) {
        if ("-".equals(value)) {
            return Consent.UNKNOWN;
        }
        if ("Y".equals(value)) {
            return Consent.YES;
        }
        if ("N".equals(value)) {
            return Consent.NO;
        }
        throw new IllegalArgumentException("Invalid consent fact in identity claim");
    }
}
