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
package com.github.games647.fastlogin.core.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public class SuccessMessage implements ProxyAuthenticatedMessage {

    public static final String SUCCESS_CHANNEL = "succ";

    // 0.5.0/F054: echoed proxy allowlist of the sending backend, appended as a
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
        // 0.5.0/F054: optional trailing authentication field; legacy payloads
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
