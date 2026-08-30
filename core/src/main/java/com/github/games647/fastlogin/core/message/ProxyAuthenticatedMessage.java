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

/**
 * A backend -> proxy channel message that echoes the backend's proxy allowlist for source
 * authentication (0.5.0/F054).
 *
 * <p>The backend knows its trusted proxy set (allowed-proxies.txt). When sending one of these
 * messages it appends that set as a trailing optional field: exactly one trusted ID -> the ID
 * itself; multiple -> comma-joined; empty set -> empty string (proxy support is effectively
 * dead then, same semantics as 0.5.0/F015).</p>
 *
 * <p>The proxy accepts the message only when its own proxy ID is part of the echoed set. This
 * is equivalent to a configuration-shared secret for single-proxy networks. In multi-proxy
 * networks a compromised backend could echo its own allowlist, which degrades the protection -
 * the goal of this scheme is to remove the previously zero-knowledge forgeability, not to
 * defend against a fully compromised backend inside the trust boundary.</p>
 */
public interface ProxyAuthenticatedMessage extends ChannelMessage {

    /**
     * Echoed proxy ID set of the sending backend.
     *
     * @return comma-joined proxy IDs, or the empty string when the backend has no trusted
     *         proxy or the payload predates this field
     */
    String getSourceProxyId();

    /**
     * Stamp the message with the sending backend's proxy allowlist.
     *
     * @param sourceProxyId comma-joined proxy IDs or the empty string
     */
    void setSourceProxyId(String sourceProxyId);
}
