/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeClaim;
import com.github.hayston1001.fastloginplus.fabric.protocol.CrackedNoticeWire;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;

/** One connection's notification, independent of the authentication decision. */
final class CrackedNoticeQueue {
    private final CrackedNoticeClaim notice;
    private boolean attempted;

    CrackedNoticeQueue(CrackedNoticeClaim notice) {
        this.notice = notice;
    }

    CrackedNoticeWire acknowledgement(String proxyIds) {
        return notice.acknowledgement(proxyIds);
    }

    synchronized void renderAfterJoin(IdentityClaim identity, Runnable render, Runnable acknowledge) {
        if (attempted || notice == null || !notice.accepts(identity)) {
            return;
        }
        attempted = true;
        render.run();
        // An uncertain acknowledgement can cause a proxy fallback duplicate. Prefer that to losing the notice.
        acknowledge.run();
    }
}
