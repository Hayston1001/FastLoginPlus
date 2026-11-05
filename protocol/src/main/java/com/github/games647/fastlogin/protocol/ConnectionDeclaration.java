/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Optional, tagged suffix of classic force messages; the legacy prefix is unchanged. */
public final class ConnectionDeclaration {
    public static final int MARKER = 0xD3;
    private static final int VERSION = 1;

    private final IdentityClaim.Decision decision;
    private final UUID forwardedUuid;
    private final boolean bedrock;

    public ConnectionDeclaration(IdentityClaim.Decision decision, UUID forwardedUuid) {
        this(decision, forwardedUuid, false);
    }

    /**
     * @param decision the connection decision
     * @param forwardedUuid the forwarded connection UUID
     * @param bedrock whether the proxy verified this as a Floodgate/Bedrock connection
     */
    public ConnectionDeclaration(IdentityClaim.Decision decision, UUID forwardedUuid, boolean bedrock) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.forwardedUuid = Objects.requireNonNull(forwardedUuid, "forwardedUuid");
        this.bedrock = bedrock;
    }

    public IdentityClaim.Decision getDecision() {
        return decision;
    }

    public UUID getForwardedUuid() {
        return forwardedUuid;
    }

    /**
     * Whether the proxy verified this connection through Floodgate.
     *
     * @return true for a Bedrock connection
     */
    public boolean isBedrock() {
        return bedrock;
    }

    public void write(DataOutput output) throws IOException {
        output.writeByte(MARKER);
        output.writeByte(VERSION);
        output.writeByte(decision == IdentityClaim.Decision.OFFLINE ? 1
                : decision == IdentityClaim.Decision.PREMIUM_VERIFIED ? 2 : 0);
        output.writeLong(forwardedUuid.getMostSignificantBits());
        output.writeLong(forwardedUuid.getLeastSignificantBits());
        // trailing: older readers stop after the UUID, so the flag is additive
        output.writeBoolean(bedrock);
    }

    /**
     * Reads the suffix after its marker has already been consumed.
     *
     * @param input the suffix payload
     * @return the connection declaration
     * @throws IOException when the payload is incomplete
     */
    public static ConnectionDeclaration readPayload(DataInput input) throws IOException {
        if (input.readUnsignedByte() != VERSION) {
            throw new IllegalArgumentException("Unsupported connection declaration version");
        }
        int code = input.readUnsignedByte();
        if (code > 2) {
            throw new IllegalArgumentException("Unknown connection declaration decision");
        }
        IdentityClaim.Decision decision = code == 1 ? IdentityClaim.Decision.OFFLINE
                : code == 2 ? IdentityClaim.Decision.PREMIUM_VERIFIED : IdentityClaim.Decision.UNDECIDED;
        UUID forwardedUuid = new UUID(input.readLong(), input.readLong());
        // the flag is part of every frame this version writes; a shorter frame is
        // truncated, not a legacy frame that may silently mean "not Bedrock"
        boolean bedrock = input.readBoolean();
        return new ConnectionDeclaration(decision, forwardedUuid, bedrock);
    }
}
