/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.message;

import com.github.games647.fastlogin.protocol.ToggleWire;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import java.io.IOException;

public class ChangePremiumMessage implements ProxyAuthenticatedMessage {

    public static final String CHANGE_CHANNEL = ToggleWire.CHANNEL;

    private String playerName;
    private boolean willEnable;
    private boolean isSourceInvoker;

    // echoed proxy allowlist of the sending backend, appended as a
    // trailing optional wire field (empty string when unset/untrusted)
    private String sourceProxyId = "";

    public ChangePremiumMessage(String playerName, boolean willEnable, boolean isSourceInvoker) {
        this.playerName = playerName;
        this.willEnable = willEnable;
        this.isSourceInvoker = isSourceInvoker;
    }

    public ChangePremiumMessage() {
        //reading from
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean shouldEnable() {
        return willEnable;
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
        return CHANGE_CHANNEL;
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
        ToggleWire.Toggle toggle;
        try {
            toggle = ToggleWire.read(input);
        } catch (IOException truncated) {
            throw new IllegalArgumentException("Truncated premium toggle payload", truncated);
        }
        this.willEnable = toggle.willEnable();
        this.playerName = toggle.playerName();
        this.isSourceInvoker = toggle.isSourceInvoker();
        this.sourceProxyId = toggle.sourceProxyId();
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
        try {
            ToggleWire.write(output, willEnable, playerName, isSourceInvoker, sourceProxyId);
        } catch (IOException impossible) {
            throw new IllegalArgumentException("Cannot serialize premium toggle payload", impossible);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "playerName='" + playerName + '\''
            + ", shouldEnable=" + willEnable
            + ", isSourceInvoker=" + isSourceInvoker
            + ", sourceProxyId='" + sourceProxyId + '\''
            + '}';
    }
}
