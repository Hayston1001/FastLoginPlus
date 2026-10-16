/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Backend capability and one-time handoff for the same-name cracked notice only. */
public final class CrackedNoticeWire {
    public static final String CHANNEL = "cracked_notice";
    public static final int MAX_BYTES = 1088;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public enum Kind {
        CAPABLE, DELIVER
    }

    private final Kind kind;
    private final String name;
    private final UUID playerId;
    private final String sourceProxyIds;
    private final UUID nonce;

    public CrackedNoticeWire(Kind kind, String name, UUID playerId, String sourceProxyIds, UUID nonce) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid notice player name");
        }
        this.name = name;
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        if (sourceProxyIds == null || sourceProxyIds.length() > 1024) {
            throw new IllegalArgumentException("Invalid notice proxy ID set");
        }
        this.sourceProxyIds = sourceProxyIds;
        this.nonce = Objects.requireNonNull(nonce, "nonce");
    }

    public Kind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public UUID playerId() {
        return playerId;
    }

    public String sourceProxyIds() {
        return sourceProxyIds;
    }

    public UUID nonce() {
        return nonce;
    }

    public CrackedNoticeWire withKind(Kind value) {
        return new CrackedNoticeWire(value, name, playerId, sourceProxyIds, nonce);
    }

    public boolean sameDelivery(CrackedNoticeWire other) {
        return other != null && name.equals(other.name) && playerId.equals(other.playerId)
                && sourceProxyIds.equals(other.sourceProxyIds) && nonce.equals(other.nonce);
    }

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(1);
            output.writeByte(kind.ordinal());
            output.writeUTF(name);
            writeUuid(output, playerId);
            output.writeUTF(sourceProxyIds);
            writeUuid(output, nonce);
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot encode cracked notice", impossible);
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length > MAX_BYTES) {
            throw new IllegalArgumentException("Oversized cracked notice");
        }
        return encoded;
    }

    public static CrackedNoticeWire decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) {
            throw new IOException("Invalid cracked notice size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = input.readUnsignedByte();
            int kind = input.readUnsignedByte();
            if (version != 1 || kind >= Kind.values().length) {
                throw new IOException("Unsupported cracked notice");
            }
            CrackedNoticeWire notice = new CrackedNoticeWire(Kind.values()[kind], input.readUTF(),
                    readUuid(input), input.readUTF(), readUuid(input));
            if (input.available() != 0) {
                throw new IOException("Trailing cracked notice bytes");
            }
            return notice;
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Malformed cracked notice", malformed);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
