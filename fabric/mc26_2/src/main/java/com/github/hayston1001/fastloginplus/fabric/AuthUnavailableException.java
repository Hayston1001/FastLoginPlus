/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

/** EasyAuth storage, API or timeout failure that prevents an account decision. */
final class AuthUnavailableException extends IllegalStateException {

    AuthUnavailableException(String message) {
        super(message);
    }

    AuthUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
