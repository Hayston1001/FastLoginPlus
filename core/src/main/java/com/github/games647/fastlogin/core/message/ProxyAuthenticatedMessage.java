/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.message;

/**
 * A backend -> proxy channel message that echoes the backend's proxy allowlist for source
 * authentication.
 *
 * <p>The backend knows its trusted proxy set (allowed-proxies.txt). When sending one of these
 * messages it appends that set as a trailing optional field: exactly one trusted ID -> the ID
 * itself; multiple -> comma-joined; empty set -> empty string (proxy support is
 * effectively dead then).</p>
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
