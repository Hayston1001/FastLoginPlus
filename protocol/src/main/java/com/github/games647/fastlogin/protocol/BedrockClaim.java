/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import java.util.UUID;

/** Optional Floodgate facts, independent of the unchanged Java identity wire format. */
public final class BedrockClaim {
    public static final String PROPERTY_NAME = "flp_bedrock";
    public static final long MAX_AGE_MILLIS = 120_000L;
    private static final String DOMAIN = "FLP-BEDROCK-V1\u0000";

    private final String name;
    private final UUID uuid;
    private final boolean linked;
    private final String autoLogin;
    private final String autoRegister;
    private final String allowConflict;
    private final long issuedAt;
    private final UUID nonce;

    // CHECKSTYLE.OFF: ParameterNumber
    public BedrockClaim(String name, UUID uuid, boolean linked, String autoLogin, String autoRegister,
                        String allowConflict, long issuedAt, UUID nonce) {
        if (name == null || name.isEmpty() || name.length() > 16 || name.contains(":")) {
            throw new IllegalArgumentException("Invalid Bedrock name");
        }
        this.name = name;
        this.uuid = java.util.Objects.requireNonNull(uuid);
        this.linked = linked;
        this.autoLogin = policy(autoLogin);
        this.autoRegister = policy(autoRegister);
        this.allowConflict = policy(allowConflict);
        this.issuedAt = issuedAt;
        this.nonce = java.util.Objects.requireNonNull(nonce);
    }
    // CHECKSTYLE.ON: ParameterNumber

    private static String policy(String value) {
        if (!"true".equals(value) && !"false".equals(value) && !"linked".equals(value)
                && !"no-conflict".equals(value)) {
            throw new IllegalArgumentException("Invalid Bedrock policy");
        }
        return value;
    }

    private String payload() {
        return "1:Y:" + name + ":" + uuid + ":" + (linked ? "Y" : "N") + ":" + autoLogin + ":"
                + autoRegister + ":" + allowConflict + ":" + issuedAt + ":" + nonce;
    }

    public String encode(UUID proxyId) {
        String payload = payload();
        return payload + ":" + IdentityClaim.sign(DOMAIN + payload, proxyId);
    }

    // Verify against the existing allowlist; transport authentication alone is insufficient.
    public static BedrockClaim verify(String value, String name, UUID uuid, Iterable<UUID> allowed, long now) {
        if (value == null || value.length() > 320) {
            throw new IllegalArgumentException("Invalid Bedrock property");
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 11 || !"1".equals(parts[0]) || !"Y".equals(parts[1])
                || !("Y".equals(parts[4]) || "N".equals(parts[4]))) {
            throw new IllegalArgumentException("Invalid Bedrock property format");
        }
        BedrockClaim claim = new BedrockClaim(parts[2], UUID.fromString(parts[3]), "Y".equals(parts[4]),
                parts[5], parts[6], parts[7], Long.parseLong(parts[8]), UUID.fromString(parts[9]));
        if (!claim.name.equals(name) || !claim.uuid.equals(uuid) || claim.issuedAt < now - MAX_AGE_MILLIS
                || claim.issuedAt > now + 5_000L) {
            throw new IllegalArgumentException("Unbound or expired Bedrock property");
        }
        String payload = value.substring(0, value.lastIndexOf(':'));
        boolean valid = false;
        for (UUID proxyId : allowed) {
            valid |= IdentityClaim.verifySignature(DOMAIN + payload, parts[10], proxyId);
        }
        if (!valid) {
            throw new IllegalArgumentException("Unauthenticated Bedrock property");
        }
        return claim;
    }

    public boolean isLinked() {
        return linked;
    }

    public String getAutoLogin() {
        return autoLogin;
    }

    public String getAutoRegister() {
        return autoRegister;
    }

    public String getAllowConflict() {
        return allowConflict;
    }

    public UUID getNonce() {
        return nonce;
    }
}
