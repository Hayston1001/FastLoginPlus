/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import java.util.UUID;

/**
 * Proxy → backend feedback about the result of a toggle/delete command that
 * was relayed through a player connection (console invocation). The proxy
 * sends this back over the carrier player's server connection so the backend
 * console — where the command was typed — sees the result instead of having
 * to check the proxy log.
 * <p>
 * The payload carries a locale key (not the rendered text) so each side
 * resolves it from its own language file, and the proxy's UUID so the backend
 * can validate the sender with the same trust model as {@link LoginActionMessage}.
 */
public class ToggleFeedbackMessage implements ChannelMessage {

    public static final String FEEDBACK_CHANNEL = "fb-st";

    private String playerName;
    private String localeId;
    private UUID proxyId;

    public ToggleFeedbackMessage(String playerName, String localeId, UUID proxyId) {
        this.playerName = playerName;
        this.localeId = localeId;
        this.proxyId = proxyId;
    }

    public ToggleFeedbackMessage() {
        //reading mode
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getLocaleId() {
        return localeId;
    }

    public UUID getProxyId() {
        return proxyId;
    }

    @Override
    public String getChannelName() {
        return FEEDBACK_CHANNEL;
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
        this.playerName = input.readUTF();
        this.localeId = input.readUTF();

        long mostSignificantBits = input.readLong();
        long leastSignificantBits = input.readLong();
        this.proxyId = new UUID(mostSignificantBits, leastSignificantBits);
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
        output.writeUTF(playerName);
        output.writeUTF(localeId);
        output.writeLong(proxyId.getMostSignificantBits());
        output.writeLong(proxyId.getLeastSignificantBits());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "playerName='" + playerName + '\''
            + ", localeId='" + localeId + '\''
            + ", proxyId=" + proxyId
            + '}';
    }
}
