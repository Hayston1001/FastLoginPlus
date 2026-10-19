/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public class SuccessMessage implements ProxyAuthenticatedMessage {

    public static final String SUCCESS_CHANNEL = "succ";

    // echoed proxy allowlist of the sending backend, appended as a
    // trailing optional wire field (empty string when unset/untrusted)
    private String sourceProxyId = "";

    @Override
    public String getSourceProxyId() {
        return sourceProxyId;
    }

    @Override
    public void setSourceProxyId(String sourceProxyId) {
        this.sourceProxyId = sourceProxyId;
    }

    @Override
    public String getChannelName() {
        return SUCCESS_CHANNEL;
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
        // optional trailing authentication field; legacy payloads
        // (older backend) are empty and surface as RuntimeException on EOF
        try {
            sourceProxyId = input.readUTF();
        } catch (RuntimeException legacyFormat) {
            sourceProxyId = "";
        }
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
        // always appended so the wire format has no arity ambiguity
        output.writeUTF(sourceProxyId);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "sourceProxyId='" + sourceProxyId + '\''
            + '}';
    }
}
