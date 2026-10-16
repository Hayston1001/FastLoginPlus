/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.slf4j.Logger;

/** Observed reasons only: a closed connection alone does not prove who kicked it. */
final class BindingTransferDiagnostics {
    enum Reason {
        PENDING_REPLACED_OR_REMOVED,
        PENDING_EXPIRED,
        PLAYER_DISCONNECTED_OR_KICKED,
        CONNECTION_NOT_ACCEPTING_MESSAGES,
        TARGET_NOT_CONFIGURED,
        TARGET_INVALID,
        NAME_UNAVAILABLE,
        ACCOUNT_CHANGED
    }

    private BindingTransferDiagnostics() {
    }

    static Reason skipReason(boolean current, boolean live, boolean accepting, boolean disconnected) {
        if (!current) {
            return Reason.PENDING_REPLACED_OR_REMOVED;
        }
        if (!live) {
            return Reason.PENDING_EXPIRED;
        }
        if (!accepting) {
            return disconnected ? Reason.PLAYER_DISCONNECTED_OR_KICKED : Reason.CONNECTION_NOT_ACCEPTING_MESSAGES;
        }
        return null;
    }

    static Reason targetProblem(String host, String port) {
        if (host.trim().isEmpty() || port.isBlank()) {
            return Reason.TARGET_NOT_CONFIGURED;
        }
        try {
            int number = Integer.parseInt(port);
            return number < 1 || number > 65535 ? Reason.TARGET_INVALID : null;
        } catch (NumberFormatException invalid) {
            return Reason.TARGET_INVALID;
        }
    }

    static void skipped(Logger logger, String name, Reason reason) {
        logger.info("Binding transfer skipped: name={} reason={}", name, reason);
    }
}
