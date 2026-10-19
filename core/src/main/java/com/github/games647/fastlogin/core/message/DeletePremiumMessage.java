/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public class DeletePremiumMessage implements ProxyAuthenticatedMessage {

    public static final String DELETE_CHANNEL = "del-st";

    private String playerName;
    private boolean isSourceInvoker;

    // echoed proxy allowlist of the sending backend, appended as a
    // trailing optional wire field (empty string when unset/untrusted)
    private String sourceProxyId = "";

    public DeletePremiumMessage(String playerName, boolean isSourceInvoker) {
        this.playerName = playerName;
        this.isSourceInvoker = isSourceInvoker;
    }

    public DeletePremiumMessage() {
        //reading from
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isSourceInvoker() {
        return isSourceInvoker;
    }

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
        return DELETE_CHANNEL;
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
        playerName = input.readUTF();
        try {
            isSourceInvoker = input.readBoolean();
        } catch (RuntimeException legacyFormat) {
            // Legacy payloads carry only the player name — treat as relayed
            // (console invoker).  ByteArrayDataInput redeclares every method
            // without checked exceptions and wraps an underlying EOFException
            // in IllegalStateException, so both EOFException and
            // IllegalStateException surface here as RuntimeException.
            isSourceInvoker = false;
            return;
        }

        // optional trailing authentication field; payloads of the
        // intermediate format (no sourceProxyId yet) surface as RuntimeException
        try {
            sourceProxyId = input.readUTF();
        } catch (RuntimeException legacyFormat) {
            sourceProxyId = "";
        }
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
        output.writeUTF(playerName);
        output.writeBoolean(isSourceInvoker);
        // always appended so the wire format has no arity ambiguity
        output.writeUTF(sourceProxyId);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "playerName='" + playerName + '\''
            + ", isSourceInvoker=" + isSourceInvoker
            + ", sourceProxyId='" + sourceProxyId + '\''
            + '}';
    }
}
