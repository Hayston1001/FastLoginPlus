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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Wire codec for the premium-toggle channel {@code fastloginplus:ch-st}.
 *
 * <p>A backend process sends this to the proxy to declare that a player name
 * consents to a premium upgrade (classic {@code /flp premium}, or the Fabric
 * binding start). The proxy records the consent and resolves the target Mojang
 * UUID; the next Mojang-verified connection for that name then carries the
 * consent onward as an identity fact.
 *
 * <p>The format lives here so the shared core message class and the separately
 * packaged Fabric module speak exactly the same bytes without sharing a
 * package.
 */
public final class ToggleWire {

    /** Channel sub-name of the toggle message. */
    public static final String CHANNEL = "ch-st";

    private ToggleWire() {
    }

    /** Toggle payload fields in wire order. */
    public static final class Toggle {

        private final boolean willEnable;
        private final String playerName;
        private final boolean isSourceInvoker;
        private final String sourceProxyId;

        Toggle(boolean willEnable, String playerName, boolean isSourceInvoker, String sourceProxyId) {
            this.willEnable = willEnable;
            this.playerName = playerName;
            this.isSourceInvoker = isSourceInvoker;
            this.sourceProxyId = sourceProxyId;
        }

        public boolean willEnable() {
            return willEnable;
        }

        public String playerName() {
            return playerName;
        }

        public boolean isSourceInvoker() {
            return isSourceInvoker;
        }

        public String sourceProxyId() {
            return sourceProxyId;
        }
    }

    /**
     * Serializes one toggle payload.
     *
     * @param output          target stream
     * @param willEnable      true for a premium-on consent, false to revoke
     * @param playerName      exact player name the consent is for
     * @param isSourceInvoker true when the named player issued the command themselves
     * @param sourceProxyId   comma-joined proxy IDs the sending backend trusts
     * @throws IOException when the stream cannot be written
     */
    public static void write(DataOutput output, boolean willEnable, String playerName,
                             boolean isSourceInvoker, String sourceProxyId) throws IOException {
        output.writeBoolean(willEnable);
        output.writeUTF(playerName);
        output.writeBoolean(isSourceInvoker);
        // always appended so the wire format has no arity ambiguity
        output.writeUTF(sourceProxyId == null ? "" : sourceProxyId);
    }

    /**
     * Parses one toggle payload.
     *
     * @param input source stream
     * @return the parsed toggle
     * @throws IOException when the mandatory prefix is truncated or malformed
     */
    public static Toggle read(DataInput input) throws IOException {
        boolean willEnable = input.readBoolean();
        String playerName = input.readUTF();
        boolean isSourceInvoker = input.readBoolean();

        // optional trailing authentication field; legacy payloads (older
        // backend) end here and surface as EOF or a runtime wrapper on it
        String sourceProxyId;
        try {
            sourceProxyId = input.readUTF();
        } catch (IOException | RuntimeException legacyFormat) {
            sourceProxyId = "";
        }
        return new Toggle(willEnable, playerName, isSourceInvoker, sourceProxyId);
    }
}
